package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{Ref, RIO, Task}
import zio.clock.Clock

trait AbstractBehavior[R, S, Msg[+_]] {
  self =>

  def makeActor(
                         actorPath: ActorPath,
                         actorConfig: ActorConfig,
                         initialState: S,
                         actorSystem: ActorSystem,
                         children: Ref[Set[ActorRef[Any]]]
  )(postStop: () => Task[Unit]): RIO[R with Clock, ActorRef[Msg]]
}
