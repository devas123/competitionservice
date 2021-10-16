package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.Messages.Command
import ActorSystem.{ActorConfig, PendingMessage}
import Messages.Command
import zio.{Fiber, Queue, Ref, RIO, Task}
import zio.clock.Clock
import zio.interop.catz._

abstract class EventSourcedBehavior[R, S, Msg[+_], Ev](persistenceId: String) extends AbstractBehavior[R, S, Msg] {
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
    id: String,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[ActorRef[Any]]]
  )(optPostStop: () => Task[Unit]): RIO[R with Clock, ActorRef[Msg]] = {

    def applyEvents(events: Seq[Ev], state: S): RIO[R, S] = events.foldLeftM(state)(sourceEvent)

    def process[A](
      msg: PendingMessage[Msg, A],
      state: Ref[S],
      context: Context[Msg],
      timers: Timers[R, Msg]
    ): RIO[R with Clock, Unit] = for {
      s <- state.get
      (fa, promise)       = msg
      receiver            = receive(context, actorConfig, s, fa, timers)
      effectfulCompleter  = (s: S, a: A) => state.set(s) *> promise.succeed(a)
      idempotentCompleter = (a: A) => promise.succeed(a)
      fullCompleter = (
        (
          ev: Command[Ev],
          sa: S => A
        ) =>
          ev match {
            case Command.Ignore =>
              idempotentCompleter(sa(s))
            case Command.Persist(ev) => for {
                _            <- persistEvents(persistenceId, ev)
                updatedState <- applyEvents(ev, s)
                res          <- effectfulCompleter(updatedState, sa(updatedState))
              } yield res
          }
      ).tupled
      _ <- receiver.foldM(e => promise.fail(e), fullCompleter)
    } yield ()

    def innerLoop(state: Ref[S], queue: Queue[PendingMessage[Msg, _]], ts: Timers[R, Msg], context: Context[Msg]) = {
      for {
        t <- (for {
          t <- queue.take
          _ <- process(t, state, context, ts)
        } yield ()).repeatUntilM(_ => queue.isShutdown).fork
        _ <- t.join.attempt
        st <- state.get
        _ <- self.postStop(actorConfig, context, st, ts).attempt
      } yield ()
    }

    for {
      events       <- getEvents(persistenceId, initialState)
      sourcedState <- applyEvents(events, initialState)
      state        <- Ref.make(sourcedState)
      queue        <- Queue.bounded[PendingMessage[Msg, _]](actorConfig.mailboxSize)
      actor = ActorRef[Msg](queue)(optPostStop)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts      = Timers[R, Msg](actor, timersMap)
      context = Context(children, actor, id, actorSystem)
      (_, msgs) <- init(actorConfig, context, sourcedState, ts)
      _         <- msgs.traverse(m => actor ! m)
      _ <- innerLoop(state, queue, ts, context).fork
    } yield actor
  }
}
