package compman.compsrv.logic.actors
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.DeadLetter
import zio.{Task, ZIO}
import zio.clock.Clock
import zio.console.Console

trait ActorRefProvider {

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
  ): ZIO[R with Clock with Console, Throwable, ActorRef[F1]]

  def select[F1](path: String): Task[ActorRef[F1]]

  def selectOption[F1](path: String): Task[Option[ActorRef[F1]]]


  def deadLetters: ActorRef[DeadLetter]
}
