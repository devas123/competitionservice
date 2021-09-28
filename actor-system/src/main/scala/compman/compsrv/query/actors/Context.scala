package compman.compsrv.query.actors

import compman.compsrv.query.actors.ActorSystem.ActorConfig
import zio.{Ref, Task, ZIO}
import zio.clock.Clock

case class Context[F[+_]](
  children: Ref[Map[String, ActorRef[Any]]],
  self: ActorRef[F],
  id: String,
  actorSystem: ActorSystem
) {

  def findChild[F1[+_]](name: String): Task[Option[ActorRef[F1]]] = {
    for { m <- children.get } yield m.get(name).map(_.asInstanceOf[ActorRef[F1]])
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
    behavior: => ActorBehavior[R, S, F1]
  ): ZIO[R with Clock, Throwable, ActorRef[F1]] = for {
    actorRef <- actorSystem.make(actorName, actorConfig, init, behavior)
    ch       <- children.get
    _        <- children.set(ch + (actorName -> actorRef.asInstanceOf[ActorRef[Any]]))
  } yield actorRef
  def select[F1[+_]](path: String): Task[ActorRef[F1]] = actorSystem.select(path)

  /* INTERNAL API */

  private[actors] def actorSystemName = actorSystem.actorSystemName

}
