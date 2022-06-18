package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.dungeon.{DeathWatch, Signal}
import zio.{Fiber, Queue, Ref, RIO, Task, URIO, ZIO}
import zio.clock.Clock
import zio.console.Console
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
    children: Ref[ContextState]
  )(optPostStop: () => Task[Unit]): RIO[R with Clock with Console, InternalActorCell[Msg]] = {
    def process(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]]
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

    for {
      queue      <- Queue.dropping[PendingMessage[Msg]](actorConfig.mailboxSize)
      watching   <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      watchedBy  <- Ref.make(Set.empty[ActorRef[Nothing]])
      timersMap  <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      stopSwitch <- Ref.make(false)
      actor = LocalActorRef[Msg](queue, actorPath)(actorSystem, stopSwitch)
      stateRef <- Ref.make(initialState)
      supervisor = actorSystem.supervisor
      ts         = Timers[R, Msg](actor, timersMap, supervisor)
      context    = Context(children, actor, actorPath, actorSystem)
      _ <- (for {
        state                <- stateRef.get
        (_, msgs, initState) <- init(actorConfig, context, state, ts)
        _                    <- stateRef.set(initState)
        _                    <- msgs.traverse(m => actor ! m)
        _ <- restartOneSupervision(context, queue, ts)(() =>
          innerLoop(msg => process(watching, watchedBy)(context, msg, stateRef, ts))(queue)
        )
      } yield ()).onExit(exit =>
        for {
          _  <- ZIO.debug(s"Actor $actor stopped with exit result $exit.")
          st <- stateRef.get
          _  <- self.postStop(actorConfig, context, st, ts).foldM(_ => URIO.unit, either => URIO.effectTotal(either))
          _  <- sendDeathwatchNotifications(watchedBy, context)
          _  <- optPostStop().ignore
        } yield ()
      ).supervised(actorSystem.supervisor).forkDaemon
    } yield InternalActorCell(actor = actor)
  }
}
