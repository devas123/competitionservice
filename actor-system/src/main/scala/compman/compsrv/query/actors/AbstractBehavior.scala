package compman.compsrv.query.actors

import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{Fiber, Ref, RIO, Task}
import zio.clock.Clock

private[actors] trait AbstractBehavior[R, S, Msg[+_]] {
  self =>

  def init(
    actorConfig: ActorConfig,
    context: Context[Msg],
    initState: S,
    timers: Timers[R, Msg]
  ): RIO[R, (Seq[Fiber[Throwable, Unit]], Seq[Msg[Any]])]

  def makeActor(
    id: String,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Map[String, ActorRef[Any]]]
  )(postStop: () => Task[Unit]): RIO[R with Clock, ActorRef[Msg]]
}
