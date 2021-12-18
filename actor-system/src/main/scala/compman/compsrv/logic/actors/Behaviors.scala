package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.dungeon.Signal
import zio.{Fiber, RIO}
import zio.clock.Clock

object Behaviors {
  implicit class BehaviorOps[R, S, Msg](behavior: ActorBehavior[R, S, Msg]) {
    def withReceive(
      handler: (Context[Msg], ActorSystem.ActorConfig, S, Msg, Timers[R, Msg]) => RIO[R, S]
    ): ActorBehavior[R, S, Msg] = {
      new DelegatingBehavior(behavior) {
        override def receive(
          context: Context[Msg],
          actorConfig: ActorSystem.ActorConfig,
          state: S,
          command: Msg,
          timers: Timers[R, Msg]
        ): RIO[R, S] = handler(context, actorConfig, state, command, timers)
      }
    }
    def withReceiveSignal(
      handler: (Context[Msg], ActorSystem.ActorConfig, S, Signal, Timers[R, Msg]) => PartialFunction[Signal, RIO[R, S]]
    ): ActorBehavior[R, S, Msg] = {
      new DelegatingBehavior(behavior) {
        override def receiveSignal(
          context: Context[Msg],
          actorConfig: ActorSystem.ActorConfig,
          state: S,
          command: Signal,
          timers: Timers[R, Msg]
        ): RIO[R, S] = handler(context, actorConfig, state, command, timers).orElse[Signal, RIO[R, S]] {
          case _ => behavior.receiveSignal(context, actorConfig, state, command, timers)
        }.apply(command)
      }
    }
    def withInit(
      handler: (
        ActorSystem.ActorConfig,
        Context[Msg],
        S,
        Timers[R, Msg]
      ) => RIO[R with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Msg], S)]
                ): ActorBehavior[R, S, Msg] = {
      new DelegatingBehavior(behavior) {
        override def init(
                           actorConfig: ActorSystem.ActorConfig,
                           context: Context[Msg],
                           initState: S,
                           timers: Timers[R, Msg]
                         ): RIO[R with Clock, (Seq[Fiber[Throwable, Unit]], Seq[Msg], S)] = handler(actorConfig, context, initState, timers)
      }
    }

    def withPostStop(
                      handler: (
                        ActorSystem.ActorConfig,
                          Context[Msg],
                          S,
                          Timers[R, Msg]
                        ) => RIO[R, Unit]
                    ): ActorBehavior[R, S, Msg] = {
      new DelegatingBehavior(behavior) {
        override def postStop(actorConfig: ActorSystem.ActorConfig, context: Context[Msg], state: S, timers: Timers[R, Msg]): RIO[R, Unit] =
          handler(actorConfig, context, state, timers)
      }
    }
  }
  def behavior[R, S, Msg]: ActorBehavior[R, S, Msg] = new MinimalBehavior[R, S, Msg]()
}
