package compman.compsrv.logic.actors
import compman.compsrv.logic.actors.dungeon.Signal
import zio.{Fiber, RIO}
import zio.clock.Clock

private[actors] class MinimalBehavior[R, S, Msg]() extends ActorBehavior[R, S, Msg] {
  override def receive(
    context: Context[Msg],
    actorConfig: ActorSystem.ActorConfig,
    state: S,
    command: Msg,
    timers: Timers[R, Msg]
  ): RIO[R, S] = RIO.effectTotal(state)
  override def receiveSignal(
    context: Context[Msg],
    actorConfig: ActorSystem.ActorConfig,
    state: S,
    command: Signal,
    timers: Timers[R, Msg]
  ): RIO[R, S] = RIO.effectTotal(state)
}

class DelegatingBehavior[R, S, Msg](behavior: ActorBehavior[R, S, Msg]) extends ActorBehavior[R, S, Msg] {
  override def receive(
    context: Context[Msg],
    actorConfig: ActorSystem.ActorConfig,
    state: S,
    command: Msg,
    timers: Timers[R, Msg]
  ): RIO[R, S] = behavior.receive(context, actorConfig, state, command, timers)

  override def receiveSignal(
    context: Context[Msg],
    actorConfig: ActorSystem.ActorConfig,
    state: S,
    command: Signal,
    timers: Timers[R, Msg]
  ): RIO[R, S] = behavior.receiveSignal(context, actorConfig, state, command, timers)

  override def init(
    actorConfig: ActorSystem.ActorConfig,
    context: Context[Msg],
    initState: S,
    timers: Timers[R, Msg]
  ): RIO[R with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Msg], S)] = behavior.init(actorConfig, context, initState, timers)

  override def postStop(
    actorConfig: ActorSystem.ActorConfig,
    context: Context[Msg],
    state: S,
    timers: Timers[R, Msg]
  ): RIO[R, Unit] = behavior.postStop(actorConfig, context, state, timers)
}
