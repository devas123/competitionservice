package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.{DeadLetter, SystemMessage}
import zio.clock.Clock
import zio.logging.Logging
import zio.{IO, RIO, Ref, Task, UIO, ZIO}

final class ActorSystem(
                         val actorSystemName: String,
                         private val refActorMap: Ref[Map[ActorPath, Any]],
                         private val parentActor: Option[ActorPath],
                         val eventStream: EventStream,
                         val _deadLetters: ActorRef[DeadLetter]
                       ) extends ActorRefProvider {
  private val RegexName = "[\\w+|\\d+|(\\-_.*$+:@&=,!~';.)|\\/]+".r

  private def buildFinalName(parentActorPath: ActorPath, actorName: String): Task[ActorPath] = actorName match {
    case "" => IO.fail(new Exception("Actor actor must not be empty"))
    case null => IO.fail(new Exception("Actor actor must not be null"))
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
    * reference to the created actor in effect that can't fail
    */
  override def make[R, S, F](
                              actorName: String,
                              actorConfig: ActorConfig,
                              init: S,
                              behavior: => AbstractBehavior[R, S, F]
                            ): RIO[R with Clock, ActorRef[F]] = for {
    map <- refActorMap.get
    path <- buildFinalName(parentActor.getOrElse(RootActorPath()), actorName)
    _ <- IO.fail(new Exception(s"Actor $path already exists")).when(map.contains(path))
    derivedSystem = new ActorSystem(actorSystemName, refActorMap, Some(path), eventStream, deadLetters)
    childrenSet <- Ref.make(Set.empty[ActorRef[Nothing]])
    actor <- behavior
      .makeActor(path, actorConfig, init, derivedSystem, childrenSet)(() => dropFromActorMap(path, childrenSet))
    _ <- refActorMap.set(map + (path -> actor))
  } yield actor

  private[actors] def dropFromActorMap(path: ActorPath, childrenRef: Ref[Set[ActorRef[Nothing]]]): Task[Unit] = for {
    _        <- refActorMap.update(_ - path)
    children <- childrenRef.get
    _        <- ZIO.foreach_(children)(_.stop)
    _        <- childrenRef.set(Set.empty)
  } yield ()

  override def select[F](path: String): Task[ActorRef[F]] = {
    for {
      actorMap <- refActorMap.get
      finalName <- buildActorName(path)
      actorRef <- selectByActorPath[F](finalName, actorMap)
    } yield actorRef

  }

  def select[F](finalName: ActorPath): Task[ActorRef[F]] = {
    for {
      actorMap <- refActorMap.get
      actorRef <- selectByActorPath[F](finalName, actorMap)
    } yield actorRef

  }


  private def selectByActorPath[F](finalName: ActorPath, actorMap: Map[ActorPath, Any]) = {
    actorMap.get(finalName) match {
      case Some(value) => for {actor <- IO.effectTotal(value.asInstanceOf[ActorRef[F]])} yield actor
      case None => IO.fail(new Exception(s"No such actor $finalName in local ActorSystem."))
    }
  }

  private def selectByActorPathOption[F](finalName: ActorPath, actorMap: Map[ActorPath, Any]) = IO.effectTotal(actorMap.get(finalName).map(_.asInstanceOf[ActorRef[F]]))

  override def deadLetters: ActorRef[DeadLetter] = _deadLetters

  override def selectOption[F1](path: String): Task[Option[ActorRef[F1]]] = for {
    actorMap <- refActorMap.get
    finalName <-
      buildActorName(path)
    result <- selectByActorPathOption[F1](finalName, actorMap)
  } yield result

  private def buildActorName[F1](path: String) = {
    if (path.startsWith("/")) IO.effectTotal(ActorPath.fromString(path))
    else buildFinalName(parentActor.getOrElse(RootActorPath()), path)
  }
}
object ActorSystem {
  private val DefaultActorMailboxSize: Int = 100
  case class ActorConfig(mailboxSize: Int = DefaultActorMailboxSize)
  private[actors] type PendingMessage[F] = Either[SystemMessage, F]

  /** Constructor for Actor System
    *
    * @param sysName
    *   - Identifier for Actor System
    * @return
    * instantiated actor system
    */
  def apply(sysName: String): ZIO[Logging with Clock, Throwable, ActorSystem] = {
    for {
      initActorRefMap <- Ref.make(Map.empty[ActorPath, Any])
      subscriptions <- Ref.make(Map.empty[Class[_], Set[ActorRef[Nothing]]])
      eventStream = EventStream(subscriptions)
      deadLetters = DeadLetterActorRef(eventStream)
      actorSystem <- IO.effect(new ActorSystem(sysName, initActorRefMap, parentActor = None, eventStream, deadLetters))
      deadLetterListener <- DeadLetterListener(actorSystem)
      _ <- eventStream.subscribe[DeadLetter](deadLetterListener)
    } yield actorSystem
  }
}
