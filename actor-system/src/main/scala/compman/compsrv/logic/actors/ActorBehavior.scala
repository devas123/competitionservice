package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.dungeon.{DeathWatch, DeathWatchNotification, Signal}
import zio.interop.catz._
import zio.{Fiber, Queue, RIO, Ref, Task}

trait ActorBehavior[R, S, Msg] extends AbstractBehavior[R, S, Msg] with DeathWatch {
  self =>
  def receive(
    context: Context[Msg],
    actorConfig: ActorConfig = ActorConfig(),
    state: S,
    command: Msg,
    timers: Timers[R, Msg]
  ): RIO[R, S]

  def receiveSignal(
    context: Context[Msg],
    actorConfig: ActorConfig = ActorConfig(),
    state: S,
    command: Signal,
    timers: Timers[R, Msg]
  ): RIO[R, S]

  def init(
    actorConfig: ActorConfig,
    context: Context[Msg],
    initState: S,
    timers: Timers[R, Msg]
  ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[Msg], S)] = RIO((Seq.empty, Seq.empty, initState))

  def postStop(actorConfig: ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] =
    timers.cancelAll()

  def makeActor(
    actorPath: ActorPath,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[ActorRef[Nothing]]]
  )(optPostStop: () => Task[Unit]): RIO[R, ActorRef[Msg]] = {
    def process(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(context: Context[Msg], msg: PendingMessage[Msg], stateRef: Ref[S], ts: Timers[R, Msg]): RIO[R, Unit] = {
      for {
        state <- stateRef.get
        command = msg
        receiver = command match {
          case Left(value) => value match {
            case signal: Signal => receiveSignal(context, actorConfig, state, signal, ts)
            case _ =>
              processSystemMessage(context, watching, watchedBy)(value).as(state)
          }
          case Right(value) => receive(context, actorConfig, state, value, ts)
        }
        completer = (s: S) => stateRef.set(s)
        _ <- receiver.foldM(e => RIO.fail(e).unit, completer)
      } yield ()
    }

    def innerLoop(
                   watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
                   watchedBy: Ref[Set[ActorRef[Nothing]]],
                   terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
                 )(queue: Queue[PendingMessage[Msg]], stateRef: Ref[S], ts: Timers[R, Msg], context: Context[Msg]) = {
      for {
        t <- (for {
          msg <- queue.take
          result <- process(watching, watchedBy, terminatedQueued)(context, msg, stateRef, ts)
        } yield ()).repeatUntilM(_ => queue.isShutdown).fork
        res <- t.join.attempt
      } yield res
    }

    def restartOne(
                    watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
                    watchedBy: Ref[Set[ActorRef[Nothing]]],
                    terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
                  )(queue: Queue[PendingMessage[Msg]], stateRef: Ref[S], ts: Timers[R, Msg], context: Context[Msg]): RIO[R, Unit] = {
      for {
        res <- innerLoop(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
        _ <- res match {
          case Left(_) => for {
            shutdown <- queue.isShutdown
            _ <- if (shutdown) Task.unit else restartOne(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
          } yield ()
          case Right(_) => Task.unit
        }
      } yield ()
    }

    for {
      queue <- Queue.dropping[PendingMessage[Msg]](actorConfig.mailboxSize)
      watching <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      watchedBy <- Ref.make(Set.empty[ActorRef[Nothing]])
      terminatedQueued <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      actor = LocalActorRef[Msg](queue, actorPath)(optPostStop, actorSystem)
      ts = Timers[R, Msg](actor, timersMap)
      stateRef <- Ref.make(initialState)
      context = Context(children, actor, actorPath, actorSystem)
      _ <- (for {
        state <- stateRef.get
        (_, msgs, initState) <- init(actorConfig, context, state, ts)
        _ <- stateRef.set(initState)
        _ <- msgs.traverse(m => actor ! m)
        _ <- restartOne(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
        st <- stateRef.get
        _ <- self.postStop(actorConfig, context, st, ts).attempt
        iAmWatchedBy <- watchedBy.get
        _ <- iAmWatchedBy.toList
          .traverse(actor => actor.asInstanceOf[ActorRef[Msg]].sendSystemMessage(DeathWatchNotification(context.self)))
      } yield ()).fork
    } yield actor
  }
}
