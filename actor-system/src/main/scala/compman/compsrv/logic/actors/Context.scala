package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.Watch
import zio.{Ref, RIO, Task, ZIO}
import zio.clock.Clock

import java.util.UUID

case class Context[F](children: Ref[Set[ActorRef[Any]]], self: ActorRef[F], actorPath: ActorPath, actorSystem: ActorSystem) {

  def stopSelf: Task[List[_]] = self.stop

  def watchWith[F1](msg: F, actorRef: ActorRef[F1]): Task[Unit] = {
    self sendSystemMessage Watch(actorRef, self, Option(msg))
  }

  def messageAdapter[In](mapping: In => F): ZIO[Any with Clock, Throwable, ActorRef[In]] = make[Any, Unit, In](
    UUID.randomUUID().toString,
    ActorConfig(),
    (),
    new ActorBehavior[Any, Unit, In] {
      override def receive(
        context: Context[In],
        actorConfig: ActorConfig,
        state: Unit,
        command: In,
        timers: Timers[Any, In]
      ): RIO[Any, Unit] = for { _ <- self ! mapping.apply(command) } yield ()
    }
  )

  def findChild[F1](name: String): Task[Option[ActorRef[F1]]] = {
    actorSystem.select[F1](name).fold(_ => None, Option(_))
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
  def make[R, S, F1](
    actorName: String,
    actorConfig: ActorConfig,
    init: S,
    behavior: => AbstractBehavior[R, S, F1]
  ): ZIO[R with Clock, Throwable, ActorRef[F1]] = for {
    ch       <- children.get
    actorRef <- actorSystem.make(actorName, actorConfig, init, behavior)
    _        <- children.set(ch + actorRef.asInstanceOf[ActorRef[Any]])
  } yield actorRef
  def select[F1](path: String): Task[ActorRef[F1]] = actorSystem.select(path)

  /* INTERNAL API */

  private[actors] def actorSystemName = actorSystem.actorSystemName

}
