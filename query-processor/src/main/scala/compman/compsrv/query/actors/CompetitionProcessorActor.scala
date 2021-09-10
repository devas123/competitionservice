package compman.compsrv.query.actors

import compman.compsrv.query.actors.CompetitionApiActor.{ActorConfig, ActorSystem, PendingMessage}
import compman.compsrv.query.actors.CompetitionProcessorActor.Context
import zio.{Fiber, Queue, Ref, RIO, Task}

trait ActorBehavior[R, S, Msg[+_]] {
  self =>
  def receive[A](
    context: Context,
    actorConfig: ActorConfig,
    state: S,
    command: Msg[A],
    timers: Timers[R, Msg]
  ): RIO[R, (S, A)]

  def init(context: Context, actorConfig: ActorConfig, initState: S, timers: Timers[R, Msg]): RIO[R, Unit]

  def makeActor[Env](actorConfig: ActorConfig, initialState: S, context: Context)(
    postStop: () => Task[Unit]
  ): RIO[Env with R, CompetitionProcessorActorRef[Msg]] = {
    def process[A](msg: PendingMessage[Msg, A], stateRef: Ref[S], ts: Timers[R, Msg]): RIO[Env with R, Unit] = {
      for {
        state <- stateRef.get
        (command, promise) = msg
        receiver           = receive(context, actorConfig, state, command, ts)
        effectfulCompleter = (s: S, a: A) => stateRef.set(s) *> promise.succeed(a)
        _ <- receiver.fold(promise.fail, events => effectfulCompleter(events._1, events._2))
      } yield ()
    }

    for {
      queue <- Queue.sliding[PendingMessage[Msg, _]](actorConfig.mailboxSize)
      actor = CompetitionProcessorActorRef[Msg](queue)(postStop)
      stateRef  <- Ref.make(initialState)
      timersMap <- Ref.make(Map.empty[String, Fiber[Throwable, Unit]])
      ts = Timers[R, Msg](actor, timersMap)
      _ <- init(context, actorConfig, initialState, ts)
      _ <- (for {
        t <- queue.take
        _ <- process(t, stateRef, ts)
      } yield ()).forever.fork
    } yield actor
  }

}

object CompetitionProcessorActor {

  case class Context(
    actorsMapRef: Ref[Map[String, CompetitionProcessorActorRef[Any]]],
    id: String,
    derivedSystem: ActorSystem
  ) {
    def self[F[+_]]: Task[CompetitionProcessorActorRef[F]] = for {
      map <- actorsMapRef.get
    } yield map(id).asInstanceOf[CompetitionProcessorActorRef[F]]
  }

}
