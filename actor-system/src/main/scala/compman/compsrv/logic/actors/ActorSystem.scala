package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.SystemMessage
import zio.{IO, Promise, Ref, RIO, Task, UIO, ZIO}
import zio.clock.Clock
import zio.internal.Hub

final class ActorSystem(
  val actorSystemName: String,
  private val refActorMap: Ref[Map[ActorPath, Any]],
  private val parentActor: Option[ActorPath],
  val eventStream: Hub[Any]
) {
  private val RegexName = "[\\w+|\\d+|(\\-_.*$+:@&=,!~';.)|\\/]+".r

  private def buildFinalName(parentActorPath: ActorPath, actorName: String): Task[ActorPath] = actorName match {
    case ""            => IO.fail(new Exception("Actor actor must not be empty"))
    case null          => IO.fail(new Exception("Actor actor must not be null"))
    case RegexName(_*) => UIO.effectTotal(parentActorPath / actorName)
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
    map  <- refActorMap.get
    path <- buildFinalName(parentActor.getOrElse(RootActorPath()), actorName)
    _    <- if (map.contains(path)) IO.fail(new Exception(s"Actor $path already exists")) else IO.unit
    derivedSystem = new ActorSystem(actorSystemName, refActorMap, Some(path), eventStream)
    childrenSet <- Ref.make(Set.empty[ActorRef[Any]])
    actor <- behavior
      .makeActor(path, actorConfig, init, derivedSystem, childrenSet)(() => dropFromActorMap(path, childrenSet))
    _ <- refActorMap.set(map + (path -> actor))
  } yield actor

  private[actors] def dropFromActorMap(path: ActorPath, childrenRef: Ref[Set[ActorRef[Any]]]): Task[Unit] = for {
    _        <- refActorMap.update(_ - path)
    children <- childrenRef.get
    _        <- ZIO.foreach_(children)(_.stop)
    _        <- childrenRef.set(Set.empty)
  } yield ()

  def select[F[+_]](path: String): Task[ActorRef[F]] = {
    for {
      actorMap <- refActorMap.get
      finalName <-
        if (path.startsWith("/")) IO.effectTotal(ActorPath.fromString(path))
        else buildFinalName(parentActor.getOrElse(RootActorPath()), path)
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
  private[actors] type PendingMessage[F[_], A] = (Either[SystemMessage[_], F[A]], Promise[Throwable, A])

  /** Constructor for Actor System
    *
    * @param sysName
    *   - Identifier for Actor System
    *
    * @return
    *   instantiated actor system
    */
  def apply(sysName: String): Task[ActorSystem] = for {
    eventStream <- IO.effectTotal(Hub.unbounded[Any])
    initActorRefMap <- Ref.make(Map.empty[ActorPath, Any])
    actorSystem     <- IO.effect(new ActorSystem(sysName, initActorRefMap, parentActor = None, eventStream))
  } yield actorSystem
}
