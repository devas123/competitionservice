package compman.compsrv.query.actors

import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{Fiber, Ref, RIO, Task}
import zio.clock.Clock

private[actors] trait AbstractBehavior[R, S, Msg[+_]] {
  self =>

  def makeActor(
    id: String,
    actorConfig: ActorConfig,
    initialState: S,
    actorSystem: ActorSystem,
    children: Ref[Map[String, ActorRef[Any]]]
  )(postStop: () => Task[Unit]): RIO[R with Clock, ActorRef[Msg]]
}