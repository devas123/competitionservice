package compman.compsrv.query.actors

import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{Ref, Task, ZIO}
import zio.clock.Clock

case class Context[F[+_]](
  children: Ref[Set[ActorRef[Any]]],
  self: ActorRef[F],
  id: String,
  actorSystem: ActorSystem
) {

  def stopSelf: Task[List[_]] = self.stop

  def findChild[F1[+_]](name: String): Task[Option[ActorRef[F1]]] = {
    actorSystem.select[F1](name).fold(_ => None, Some(_))
  }

  /** Creates actor and registers it to dependent actor system
    *
    * @param actorName
    *   name of the actor
    * @param init
    *   - initial state
    * @param behavior
    *   - actor's behavior description
    * @tparam S
    *   - state type
    * @tparam F1
    *   - DSL type
    * @return
    *   reference to the created actor in effect that can't fail
    */
  def make[R, S, F1[+_]](
    actorName: String,
    actorConfig: ActorConfig,
    init: S,
    behavior: => AbstractBehavior[R, S, F1]
  ): ZIO[R with Clock, Throwable, ActorRef[F1]] = for {
    ch       <- children.get
    actorRef <- actorSystem.make(actorName, actorConfig, init, behavior)
    _        <- children.set(ch + actorRef.asInstanceOf[ActorRef[Any]])
  } yield actorRef
  def select[F1[+_]](path: String): Task[ActorRef[F1]] = actorSystem.select(path)

  /* INTERNAL API */

  private[actors] def actorSystemName = actorSystem.actorSystemName

}
