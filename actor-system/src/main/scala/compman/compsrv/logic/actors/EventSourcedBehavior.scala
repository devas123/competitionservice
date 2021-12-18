package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.EventSourcedMessages.Command
import compman.compsrv.logic.actors.dungeon.{DeathWatch, DeathWatchNotification}
import zio.{Fiber, Queue, Ref, RIO, Task, URIO, ZIO}
import zio.clock.Clock
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
  ): RIO[R, (Command[Ev], S => Unit)]

  def sourceEvent(state: S, event: Ev): RIO[R, S]
  def getEvents(persistenceId: String, state: S): RIO[R, Seq[Ev]]
  def persistEvents(persistenceId: String, events: Seq[Ev]): RIO[R, Unit]

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
    children: Ref[Set[InternalActorCell[Nothing]]]
  )(optPostStop: () => Task[Unit]): RIO[R with Clock, InternalActorCell[Msg]] = {

    def applyEvents(events: Seq[Ev], state: S): RIO[R, S] = events.foldLeftM(state)(sourceEvent)

    def process(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(msg: PendingMessage[Msg], state: Ref[S], context: Context[Msg], timers: Timers[R, Msg]): RIO[R with Clock, Unit] =
      for {
        s <- state.get
        fa = msg
        receiver = fa match {
          case Left(value) => processSystemMessage(context, watching, watchedBy)(value)
              .as((Command.Ignore, (_: S) => ()))
          case Right(value) => receive(context, actorConfig, s, value, timers)
        }
        effectfulCompleter  = (s: S) => state.set(s)
        idempotentCompleter = () => RIO.unit
        fullCompleter = (
          (
            ev: Command[Ev],
            sa: S => Unit
          ) =>
            ev match {
              case Command.Ignore => sa(s); idempotentCompleter()
              case Command.Persist(ev) => for {
                  _            <- persistEvents(persistenceId, ev)
                  updatedState <- applyEvents(ev, s)
                  _            <- RIO(sa(updatedState))
                  res          <- effectfulCompleter(updatedState)
                } yield res
            }
        ).tupled
        _ <- receiver.foldM(e => ZIO.fail(e), fullCompleter)
      } yield ()

    def innerLoop(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(state: Ref[S], queue: Queue[PendingMessage[Msg]], ts: Timers[R, Msg], context: Context[Msg]) = {
      (for {
        t <- queue.take
        _ <- process(watching, watchedBy, terminatedQueued)(t, state, context, ts)
      } yield ()).repeatUntilM(_ => queue.isShutdown)
    }

    def restartOneSupervision(
      watching: Ref[Map[ActorRef[Nothing], Option[Any]]],
      watchedBy: Ref[Set[ActorRef[Nothing]]],
      terminatedQueued: Ref[Map[ActorRef[Nothing], Option[Any]]]
    )(
      queue: Queue[PendingMessage[Msg]],
      stateRef: Ref[S],
      ts: Timers[R, Msg],
      context: Context[Msg]
    ): RIO[R with Clock, Unit] = {
      for {
        res <- innerLoop(watching, watchedBy, terminatedQueued)(stateRef, queue, ts, context).foldM(
          err =>
            ZIO.debug(s"Error during receive loop $err") *>
              restartOneSupervision(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context),
          _ => ZIO.unit
        )
      } yield res
    }

    for {
      queue            <- Queue.bounded[PendingMessage[Msg]](actorConfig.mailboxSize)
      watching         <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      watchedBy        <- Ref.make(Set.empty[ActorRef[Nothing]])
      terminatedQueued <- Ref.make(Map.empty[ActorRef[Nothing], Option[Any]])
      actor = LocalActorRef[Msg](queue, actorPath)(optPostStop, actorSystem)
      stateRef  <- Ref.make(initialState)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts      = Timers[R, Msg](actor, timersMap)
      context = Context(children, actor, actorPath, actorSystem)
      actorLoop <- (for {
        events       <- getEvents(persistenceId, initialState)
        sourcedState <- applyEvents(events, initialState)
        _            <- stateRef.set(sourcedState)
        (_, msgs)    <- init(actorConfig, context, sourcedState, ts)
        _            <- msgs.traverse(m => actor ! m)
        _            <- restartOneSupervision(watching, watchedBy, terminatedQueued)(queue, stateRef, ts, context)
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
    } yield InternalActorCell(actor, actorLoop)
  }
}
