package compman.compsrv.logic.actors

import cats.arrow.FunctionK
import cats.~>
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{Ref, RIO, Task, ZIO}
import zio.clock.Clock

import java.util.UUID

case class Context[F[+_]](children: Ref[Set[ActorRef[Any]]], self: ActorRef[F], id: String, actorSystem: ActorSystem) {

  def stopSelf: Task[List[_]] = self.stop

  def messageAdapter[In[+_]](mapping: In ~> F): ZIO[Any with Clock, Throwable, ActorRef[In]] = make[Any, Unit, In](
    UUID.randomUUID().toString,
    ActorConfig(),
    (),
    new ActorBehavior[Any, Unit, In] {
      override def receive[A](
        context: Context[In],
        actorConfig: ActorConfig,
        state: Unit,
        command: In[A],
        timers: Timers[Any, In]
      ): RIO[Any, (Unit, A)] = for { _ <- self ! mapping.apply(command) } yield ((), ().asInstanceOf[A])
    }
  )

  def findChild[F1[+_]](name: String): Task[Option[ActorRef[F1]]] = {
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
