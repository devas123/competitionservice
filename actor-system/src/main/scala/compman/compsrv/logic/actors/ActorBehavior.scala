package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actors
import compman.compsrv.logic.actors.ActorSystem.{ActorConfig, PendingMessage}
import zio.interop.catz._
import zio.{Fiber, Queue, RIO, Ref, Task}

trait ActorBehavior[R, S, Msg[+_]] extends AbstractBehavior[R, S, Msg] {
  self =>
  def receive[A](
    context: Context[Msg],
    actorConfig: ActorConfig = ActorConfig(),
    state: S,
    command: Msg[A],
    timers: Timers[R, Msg]
  ): RIO[R, (S, A)]

  def init(
            actorConfig: ActorConfig,
            context: Context[Msg],
            initState: S,
            timers: Timers[R, Msg]
          ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[Msg[Any]], S)] = RIO((Seq.empty, Seq.empty, initState))

  def postStop(actorConfig: ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] =
    RIO(())

  def makeActor(
    id: String,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Set[ActorRef[Any]]]
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

    def innerLoop(queue: Queue[PendingMessage[Msg, _]], stateRef: Ref[S], ts: Timers[R, Msg], context: Context[Msg]) = {
      for {
        t <- (for {
          msg <- queue.take
          _ <- process(context, msg, stateRef, ts).attempt
        } yield ()).repeatUntilM(_ => queue.isShutdown).fork
        _ <- t.join.attempt
        st <- stateRef.get
        _ <- self.postStop(actorConfig, context, st, ts).attempt
      } yield ()
    }

    for {
      queue <- Queue.bounded[PendingMessage[Msg, _]](actorConfig.mailboxSize)
      actor = actors.ActorRef[Msg](queue)(optPostStop)
      _ <- (for {
        stateRef <- Ref.make(initialState)
        timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
        ts = Timers[R, Msg](actor, timersMap)
        context = Context(children, actor, id, actorSystem)
        (_, msgs, initState) <- init(actorConfig, context, initialState, ts)
        _ <- stateRef.set(initState)
        _ <- msgs.traverse(m => actor ! m)
        _ <- innerLoop(queue, stateRef, ts, context).fork
      } yield ()).fork
    } yield actor
  }

}
