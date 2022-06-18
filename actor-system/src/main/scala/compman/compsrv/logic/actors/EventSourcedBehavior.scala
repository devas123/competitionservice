package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.EventSourcedMessages.EventSourcingCommand
import compman.compsrv.logic.actors.dungeon.DeathWatch
import zio.{Exit, Fiber, Queue, Ref, RIO, Task, URIO, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._

abstract class EventSourcedBehavior[R, S, Msg, Ev](persistenceId: String)
    extends AbstractBehavior[R, S, Msg] with DeathWatch {
  self =>

  def postStop(actorConfig: ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] =
    RIO(())

  def receive(
    context: Context[Msg],
    actorConfig: ActorConfig,
    state: S,
    command: Msg,
    timers: Timers[R, Msg]
  ): RIO[R, (EventSourcingCommand[Ev], S => Unit)]

  def sourceEvent(state: S, event: Ev): RIO[R, S]
  def getEvents(persistenceId: String, state: S): RIO[R, Seq[Ev]]
  def persistEvents(persistenceId: String, events: Seq[Ev]): URIO[R, Unit]

  def init(
    actorConfig: ActorConfig,
    context: Context[Msg],
    initState: S,
    timers: Timers[R, Msg]
  ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[Msg])] = RIO((Seq.empty, Seq.empty))

  override final def makeActor(
    actorPath: ActorPath,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[ContextState]
  )(optPostStop: () => Task[Unit]): RIO[R with Clock with Console, InternalActorCell[Msg]] = {

    def applyEvents(events: Seq[Ev], state: S): RIO[R, S] = events.foldLeftM(state)((s, e) => sourceEvent(s, e))

    def process(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(
      msg: PendingMessage[Msg],
      state: Ref[S],
      context: Context[Msg],
      timers: Timers[R, Msg]
    ): RIO[R with Clock with Console, Unit] = for {
      s <- state.get
      fa = msg
      receiver = fa match {
        case Left(value) => processSystemMessage(context, watching, watchedBy)(value)
            .as((EventSourcingCommand.Ignore, (_: S) => ()))
        case Right(value) => receive(context, actorConfig, s, value, timers)
      }
      effectfulCompleter  = (s: S) => state.set(s)
      idempotentCompleter = () => RIO.unit
      fullCompleter = (
        (
          ev: EventSourcingCommand[Ev],
          sa: S => Unit
        ) =>
          ev match {
            case EventSourcingCommand.Ignore => sa(s); idempotentCompleter()
            case EventSourcingCommand.Persist(ev) => for {
                updatedState <- applyEvents(ev, s).onExit {
                  case Exit.Success(value) => persistEvents(persistenceId, ev) *> URIO(value)
                  case Exit.Failure(cause) => ZIO.debug(s"Error while applying events: $cause") *> URIO(state)
                }
                _   <- RIO(sa(updatedState))
                res <- effectfulCompleter(updatedState)
              } yield res
          }
      ).tupled
      _ <- receiver.foldM(e => ZIO.fail(e), fullCompleter)
    } yield ()

    for {
      queue            <- Queue.bounded[PendingMessage[Msg]](actorConfig.mailboxSize)
      watching         <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      watchedBy        <- Ref.make(Set.empty[ActorRef[Nothing]])
      terminatedQueued <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      stopSwitch       <- Ref.make(false)
      stateRef         <- Ref.make(initialState)
      timersMap        <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      supervisor = actorSystem.supervisor
      actor      = LocalActorRef[Msg](queue, actorPath)(actorSystem, stopSwitch)
      ts         = Timers[R, Msg](actor, timersMap, supervisor)
      context    = Context(children, actor, actorPath, actorSystem)
      shouldRunPostStop <- Ref.make(false)
      _ <- (for {
        events       <- getEvents(persistenceId, initialState)
        sourcedState <- applyEvents(events, initialState)
        _            <- stateRef.set(sourcedState)
        (_, msgs)    <- init(actorConfig, context, sourcedState, ts)
        _            <- shouldRunPostStop.set(true)
        _            <- ZIO.foreach_(msgs)(m => actor ! m)
        _ <- restartOneSupervision(context, queue, ts)(() =>
          innerLoop(msg => process(watching, watchedBy, terminatedQueued)(msg, stateRef, context, ts))(queue)
        )
      } yield ()).onExit(exit =>
        for {
          _  <- ZIO.debug(s"Actor $actor stopped with exit result $exit.")
          st <- stateRef.get
          _ <- self.postStop(actorConfig, context, st, ts).foldM(_ => URIO.unit, either => URIO.effectTotal(either))
            .whenM(shouldRunPostStop.get)
          _ <- sendDeathwatchNotifications(watchedBy, context)
          _ <- optPostStop().ignore
        } yield ()
      ).supervised(actorSystem.supervisor).forkDaemon
    } yield InternalActorCell(actor)
  }
}
