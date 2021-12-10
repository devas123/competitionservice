package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import compman.compsrv.logic.actors.EventSourcedMessages.Command
import compman.compsrv.logic.actors.dungeon.{DeathWatch, DeathWatchNotification}
import zio.{Fiber, Queue, Ref, RIO, Task}
import zio.clock.Clock
import zio.interop.catz._

abstract class EventSourcedBehavior[R, S, Msg[+_], Ev](persistenceId: String)
    extends AbstractBehavior[R, S, Msg] with DeathWatch[Msg] {
  self =>

  def postStop(actorConfig: ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] =
    RIO(())

  def receive[A](
    context: Context[Msg],
    actorConfig: ActorConfig,
    state: S,
    command: Msg[A],
    timers: Timers[R, Msg]
  ): RIO[R, (Command[Ev], S => A)]

  def sourceEvent(state: S, event: Ev): RIO[R, S]
  def getEvents(persistenceId: String, state: S): RIO[R, Seq[Ev]]
  def persistEvents(persistenceId: String, events: Seq[Ev]): RIO[R, Unit]

  def init(
    actorConfig: ActorConfig,
    context: Context[Msg],
    initState: S,
    timers: Timers[R, Msg]
  ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[Msg[Any]])] = RIO((Seq.empty, Seq.empty))

  override final def makeActor(
    actorPath: ActorPath,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[ActorRef[Any]]]
  )(optPostStop: () => Task[Unit]): RIO[R with Clock, ActorRef[Msg]] = {

    def applyEvents(events: Seq[Ev], state: S): RIO[R, S] = events.foldLeftM(state)(sourceEvent)

    def process[A](
      watching: Ref[Map[Any, Option[Any]]],
      watchedBy: Ref[Set[Any]],
      terminatedQueued: Ref[Map[Any, Option[Any]]]
    )(
      msg: PendingMessage[Msg, A],
      state: Ref[S],
      context: Context[Msg],
      timers: Timers[R, Msg]
    ): RIO[R with Clock, Unit] = for {
      s <- state.get
      (fa, promise) = msg
      receiver = fa match {
        case Left(value) => processSystemMessage(context, watching, watchedBy)(value)
            .as((Command.Ignore, (_: S) => ().asInstanceOf[A]))
        case Right(value) => receive(context, actorConfig, s, value, timers)
      }
      effectfulCompleter  = (s: S, a: A) => state.set(s) *> promise.succeed(a)
      idempotentCompleter = (a: A) => promise.succeed(a)
      fullCompleter = (
        (
          ev: Command[Ev],
          sa: S => A
        ) =>
          ev match {
            case Command.Ignore => idempotentCompleter(sa(s))
            case Command.Persist(ev) => for {
                _            <- persistEvents(persistenceId, ev)
                updatedState <- applyEvents(ev, s)
                res          <- effectfulCompleter(updatedState, sa(updatedState))
              } yield res
          }
      ).tupled
      _ <- receiver.foldM(e => promise.fail(e), fullCompleter)
    } yield ()

    def innerLoop(
      watching: Ref[Map[Any, Option[Any]]],
      watchedBy: Ref[Set[Any]],
      terminatedQueued: Ref[Map[Any, Option[Any]]]
    )(state: Ref[S], queue: Queue[PendingMessage[Msg, _]], ts: Timers[R, Msg], context: Context[Msg]) = {
      import cats.implicits._
      import zio.interop.catz._
      for {
        t <- (for {
          t <- queue.take
          _ <- process(watching, watchedBy, terminatedQueued)(t, state, context, ts)
        } yield ()).repeatUntilM(_ => queue.isShutdown).fork
        _  <- t.join.attempt
        st <- state.get
        _  <- self.postStop(actorConfig, context, st, ts).attempt
        iAmWatchedBy <- watchedBy.get
        _ <- iAmWatchedBy.toList.traverse(actor => actor.asInstanceOf[ActorRef[Msg]].sendSystemMessage(DeathWatchNotification(context.self)))
      } yield ()
    }

    for {
      queue            <- Queue.bounded[PendingMessage[Msg, _]](actorConfig.mailboxSize)
      watching         <- Ref.make(Map.empty[Any, Option[Any]])
      watchedBy        <- Ref.make(Set.empty[Any])
      terminatedQueued <- Ref.make(Map.empty[Any, Option[Any]])
      actor = ActorRef[Msg](queue, actorPath)(optPostStop)
      _ <- (for {
        events       <- getEvents(persistenceId, initialState)
        sourcedState <- applyEvents(events, initialState)
        state        <- Ref.make(sourcedState)
        timersMap    <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
        ts      = Timers[R, Msg](actor, timersMap)
        context = Context(children, actor, actorPath, actorSystem)
        (_, msgs) <- init(actorConfig, context, sourcedState, ts)
        _         <- msgs.traverse(m => actor ! m)
        _         <- innerLoop(watching, watchedBy, terminatedQueued)(state, queue, ts, context)
      } yield ()).fork
    } yield actor
  }
}
