package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.dungeon.{DeathWatch, DeathWatchNotification, Signal}
import zio.{Fiber, Queue, Ref, RIO, Task}
import zio.interop.catz._

import scala.annotation.nowarn

trait ActorBehavior[R, S, Msg] extends AbstractBehavior[R, S, Msg] with DeathWatch[Msg] {
  self =>
  def receive(
    context: Context[Msg],
    actorConfig: ActorConfig = ActorConfig(),
    state: S,
    command: Msg,
    timers: Timers[R, Msg]
  ): RIO[R, S]

  @nowarn
  def receiveSignal(
    context: Context[Msg],
    actorConfig: ActorConfig = ActorConfig(),
    state: S,
    command: Signal,
    timers: Timers[R, Msg]
  ): RIO[R, S] = RIO.unit.as(state)

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
    children: Ref[Set[ActorRef[Any]]]
  )(optPostStop: () => Task[Unit]): RIO[R, ActorRef[Msg]] = {
    def process(
      watching: Ref[Map[ActorRef[Any], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Any]]],
      terminatedQueued: Ref[Map[ActorRef[Any], Option[Any]]]
    )(context: Context[Msg], msg: PendingMessage[Msg], stateRef: Ref[S], ts: Timers[R, Msg]): RIO[R, Unit] = {
      for {
        state <- stateRef.get
        (command, promise) = msg
        receiver = command match {
          case Left(value) => value match {
              case signal: Signal => receiveSignal(context, actorConfig, state, signal, ts)
              case _              => processSystemMessage(context, watching, watchedBy)(value).as(state)
            }
          case Right(value) => receive(context, actorConfig, state, value, ts)
        }
        completer = (s: S) => stateRef.set(s) *> promise.succeed(())
        _ <- receiver.foldM(promise.fail, completer)
      } yield ()
    }

    def innerLoop(
      watching: Ref[Map[ActorRef[Any], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Any]]],
      terminatedQueued: Ref[Map[ActorRef[Any], Option[Any]]]
    )(queue: Queue[PendingMessage[Msg]], stateRef: Ref[S], ts: Timers[R, Msg], context: Context[Msg]) = {
      for {
        t <- (for {
          msg <- queue.take
          _   <- process(watching, watchedBy, terminatedQueued)(context, msg, stateRef, ts).attempt
        } yield ()).repeatUntilM(_ => queue.isShutdown).fork
        _            <- t.join.attempt
        st           <- stateRef.get
        _            <- self.postStop(actorConfig, context, st, ts).attempt
        iAmWatchedBy <- watchedBy.get
        _ <- iAmWatchedBy.toList
          .traverse(actor => actor.asInstanceOf[ActorRef[Msg]].sendSystemMessage(DeathWatchNotification(context.self)))
      } yield ()
    }

    for {
      queue            <- Queue.dropping[PendingMessage[Msg]](actorConfig.mailboxSize)
      watching         <- Ref.make(Map.empty[ActorRef[Any], Option[Any]])
      watchedBy        <- Ref.make(Set.empty[ActorRef[Any]])
      terminatedQueued <- Ref.make(Map.empty[ActorRef[Any], Option[Any]])
      timersMap        <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      actor = LocalActorRef[Msg](queue, actorPath)(optPostStop)
      ts    = Timers[R, Msg](actor, timersMap)
      _ <- (for {
        stateRef <- Ref.make(initialState)
        context = Context(children, actor, actorPath, actorSystem)
        (_, msgs, initState) <- init(actorConfig, context, initialState, ts)
        _                    <- stateRef.set(initState)
        _                    <- msgs.traverse(m => actor ! m)
        _                    <- innerLoop(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
      } yield ()).fork
    } yield actor
  }
}
