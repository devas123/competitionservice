package compman.compsrv.logic.actors

import ActorSystem.ActorConfig
import zio.{IO, Promise, Ref, RIO, Task, UIO, ZIO}
import zio.clock.Clock

final class ActorSystem(
  val actorSystemName: String,
  private val refActorMap: Ref[Map[String, Any]],
  private val parentActor: Option[String]
) {
  private val RegexName = "[\\w+|\\d+|(\\-_.*$+:@&=,!~';.)|\\/]+".r

  private def buildFinalName(parentActorName: String, actorName: String): Task[String] = actorName match {
    case ""            => IO.fail(new Exception("Actor actor must not be empty"))
    case null          => IO.fail(new Exception("Actor actor must not be null"))
    case RegexName(_*) => UIO.effectTotal(parentActorName + "/" + actorName)
    case _ => IO.fail(new Exception(s"Invalid actor name provided $actorName. Valid symbols are -_.*$$+:@&=,!~';"))
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
    * @tparam F
    *   - DSL type
    * @return
    *   reference to the created actor in effect that can't fail
    */
  def make[R, S, F[+_]](
    actorName: String,
    actorConfig: ActorConfig,
    init: S,
    behavior: => AbstractBehavior[R, S, F]
  ): RIO[R with Clock, ActorRef[F]] = for {
    map       <- refActorMap.get
    finalName <- buildFinalName(parentActor.getOrElse(""), actorName)
    _         <- if (map.contains(finalName)) IO.fail(new Exception(s"Actor $finalName already exists")) else IO.unit
    derivedSystem = new ActorSystem(actorSystemName, refActorMap, Some(finalName))
    childrenSet <- Ref.make(Set.empty[ActorRef[Any]])
    actor <- behavior.makeActor(finalName, actorConfig, init, derivedSystem, childrenSet)(() =>
      dropFromActorMap(finalName, childrenSet)
    )
    _ <- refActorMap.set(map + (finalName -> actor))
  } yield actor

  private[actors] def dropFromActorMap(path: String, childrenRef: Ref[Set[ActorRef[Any]]]): Task[Unit] = for {
    _        <- refActorMap.update(_ - path)
    children <- childrenRef.get
    _        <- ZIO.foreach_(children)(_.stop)
    _        <- childrenRef.set(Set.empty)
  } yield ()

  def select[F[+_]](path: String): Task[ActorRef[F]] = {
    for {
      actorMap  <- refActorMap.get
      finalName <- if (path.startsWith("/")) IO.effectTotal(path) else buildFinalName(parentActor.getOrElse(""), path)
      actorRef <- actorMap.get(finalName) match {
        case Some(value) => for { actor <- IO.effectTotal(value.asInstanceOf[ActorRef[F]]) } yield actor
        case None        => IO.fail(new Exception(s"No such actor $path in local ActorSystem."))
      }
    } yield actorRef

  }

}
object ActorSystem {
  private val DefaultActorMailboxSize: Int = 100
  case class ActorConfig(mailboxSize: Int = DefaultActorMailboxSize)
  private[actors] type PendingMessage[F[_], A] = (F[A], Promise[Throwable, A])

  /** Constructor for Actor System
    *
    * @param sysName
    *   - Identifier for Actor System
    *
    * @return
    *   instantiated actor system
    */
  def apply(sysName: String): Task[ActorSystem] = for {
    initActorRefMap <- Ref.make(Map.empty[String, Any])
    actorSystem     <- IO.effect(new ActorSystem(sysName, initActorRefMap, parentActor = None))
  } yield actorSystem

}