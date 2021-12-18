package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.dungeon.{DeathWatch, DeathWatchNotification, Signal}
import zio.{Exit, Fiber, Queue, Ref, RIO, Task, URIO, ZIO}
import zio.clock.Clock
import zio.interop.catz._

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
  ): RIO[R with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Msg], S)] = RIO((Seq.empty, Seq.empty, initState))

  def postStop(actorConfig: ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] =
    timers.cancelAll()

  override final def makeActor(
    actorPath: ActorPath,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[InternalActorCell[Nothing]]]
  )(optPostStop: () => Task[Unit]): RIO[R with Clock, InternalActorCell[Msg]] = {
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
              case _              => processSystemMessage(context, watching, watchedBy)(value).as(state)
            }
          case Right(value) => receive(context, actorConfig, state, value, ts)
        }
        completer = (s: S) => stateRef.set(s)
        res <- receiver.foldM(e => RIO.fail(e).unit, completer)
      } yield res
    }

    def innerLoop(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(queue: Queue[PendingMessage[Msg]], stateRef: Ref[S], ts: Timers[R, Msg], context: Context[Msg]) = (for {
      thread <- queue.take.fork
      msg    <- thread.await
      _ <- msg match {
        case Exit.Success(value) => process(watching, watchedBy, terminatedQueued)(context, value, stateRef, ts)
        case Exit.Failure(cause) => RIO.unit
      }
    } yield ()).repeatUntilM(_ => queue.isShutdown)

    def restartOneSupervision(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(queue: Queue[PendingMessage[Msg]], stateRef: Ref[S], ts: Timers[R, Msg], context: Context[Msg]): RIO[R, Unit] = {
      for {
        res <- innerLoop(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context).attempt
        _ <- res match {
          case Left(_)  => restartOneSupervision(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
          case Right(_) => ZIO.unit
        }
      } yield ()
    }

    for {
      queue            <- Queue.dropping[PendingMessage[Msg]](actorConfig.mailboxSize)
      watching         <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      watchedBy        <- Ref.make(Set.empty[ActorRef[Nothing]])
      terminatedQueued <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      timersMap        <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      actor = LocalActorRef[Msg](queue, actorPath)(optPostStop, actorSystem)
      ts    = Timers[R, Msg](actor, timersMap)
      stateRef <- Ref.make(initialState)
      context = Context(children, actor, actorPath, actorSystem)
      actorLoop <- (for {
        state                <- stateRef.get
        (_, msgs, initState) <- init(actorConfig, context, state, ts)
        _                    <- stateRef.set(initState)
        _                    <- msgs.traverse(m => actor ! m)
        _ <- restartOneSupervision(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
      } yield ()).onExit(_ =>
        for {
          st <- stateRef.get
          _  <- self.postStop(actorConfig, context, st, ts).foldM(_ => URIO.unit, either => URIO.effectTotal(either))
          iAmWatchedBy <- watchedBy.get
          _ <- iAmWatchedBy.toList.traverse(actor =>
            actor.asInstanceOf[ActorRef[Msg]].sendSystemMessage(DeathWatchNotification(context.self))
          ).foldM(_ => URIO.unit, either => URIO.effectTotal(either))
        } yield ()
      ).fork
    } yield InternalActorCell(actor = actor, actorFiber = actorLoop)
  }
}
