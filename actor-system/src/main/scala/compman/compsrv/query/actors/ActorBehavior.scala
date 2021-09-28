package compman.compsrv.query.actors

import cats.implicits._
import compman.compsrv.query.actors.ActorSystem.{ActorConfig, PendingMessage}
import zio.{Fiber, Queue, Ref, RIO, Task}
import zio.interop.catz._

trait ActorBehavior[R, S, Msg[+_]] extends AbstractBehavior[R, S, Msg] {
  self =>
  def receive[A](
    context: Context[Msg],
    actorConfig: ActorConfig,
    state: S,
    command: Msg[A],
    timers: Timers[R, Msg]
  ): RIO[R, (S, A)]

  def init(
    actorConfig: ActorConfig,
    context: Context[Msg],
    initState: S,
    timers: Timers[R, Msg]
  ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[Msg[Any]])] = RIO((Seq.empty, Seq.empty))

  def postStop(actorConfig: ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] = RIO(())

  def makeActor(
    id: String,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Map[String, ActorRef[Any]]]
  )(optPostStop: () => Task[Unit]): RIO[R, ActorRef[Msg]] = {
    def process[A](
      context: Context[Msg],
      msg: PendingMessage[Msg, A],
      stateRef: Ref[S],
      ts: Timers[R, Msg]
    ): RIO[R, Unit] = {
      for {
        state <- stateRef.get
        (command, promise) = msg
        receiver           = receive(context, actorConfig, state, command, ts)
        completer          = ((s: S, a: A) => stateRef.set(s) *> promise.succeed(a)).tupled
        _ <- receiver.foldM(promise.fail, completer)
      } yield ()
    }

    for {
      queue <- Queue.sliding[PendingMessage[Msg, _]](actorConfig.mailboxSize)
      actor = ActorRef[Msg](queue)(optPostStop)
      stateRef  <- Ref.make(initialState)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts      = Timers[R, Msg](actor, timersMap)
      context = Context(children, actor, id, actorSystem)
      (_, msgs) <- init(actorConfig, context, initialState, ts)
      _         <- msgs.traverse(m => actor ! m)
      _ <- (for {
        t <- queue.take
        _ <- process(context, t, stateRef, ts)
      } yield ()).forever.fork.onTermination(_ =>
        for {
          st <- stateRef.get
          _  <- self.postStop(actorConfig, context, st, ts).attempt.ignore
        } yield ()
      )
    } yield actor
  }

}