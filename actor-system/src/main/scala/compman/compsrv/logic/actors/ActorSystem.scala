package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.dungeon.{DeadLetter, SystemMessage}
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging
import zio.{Chunk, Exit, Fiber, IO, RIO, Ref, Supervisor, Task, UIO, URIO, ZIO, ZManaged}

final class ActorSystem(
                         val actorSystemName: String,
                         private val refActorMap: Ref[Map[ActorPath, InternalActorCell[Nothing]]],
                         private val parentActor: Option[ActorPath],
                         val eventStream: EventStream,
                         val _deadLetters: ActorRef[DeadLetter],
                         val supervisor: Supervisor[Chunk[Fiber.Runtime[Any, Any]]]
                       ) extends ActorRefProvider {
  private val RegexName = "[\\w+|\\d+|(\\-_.*$+:@&=,!~';.)|\\/]+".r

  //TODO
  private[actors] def shutdown(): URIO[Clock, Unit] = ZIO.debug(s"Actor system $actorSystemName shutdown") *> (for {
    runningActors <- refActorMap.get
    _ <- URIO.foreach_(runningActors)(cell => cell._2.stop.ignore)
    _ <- awaitShutdown()
  } yield ())

  private[actors] def awaitShutdown(): URIO[Clock, Unit] = for {
    fibers <- supervisor.value
    _ <- (for {
      dumps <- ZIO.foreach(fibers)(_.dump)
      dumpsStr <- ZIO.foreach(dumps)(_.prettyPrintM)
      _ <- ZIO.foreach_(dumpsStr)(ZIO.debug)
      _ <- (RIO.debug(s"Waiting for ${fibers.length} fibers to stop. ") *> awaitShutdown()).delay(1000.millis)
    } yield ()).when(fibers.nonEmpty)
    _ <- RIO.debug("All fibers stopped.")
  } yield ()

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
                            ): RIO[R with Clock with Console, ActorRef[F]] = for {
    path <- buildFinalName(parentActor.getOrElse(RootActorPath()), actorName)
    updated <- refActorMap.modify(refMap =>
      if (refMap.contains(path)) {
        (false, refMap)
      }
      else {
        (true, refMap + (path -> InternalActorCell(new MinimalActorRef[F] {}, Fiber.unit)))
      }
    )
    _ <- IO.fail(new Exception(s"Actor $path already exists")).unless(updated)
    derivedSystem = new ActorSystem(actorSystemName, refActorMap, Some(path), eventStream, deadLetters, supervisor)
    childrenSet <- Ref.make(Set.empty[InternalActorCell[Nothing]])
    actor <- behavior
      .makeActor(path, actorConfig, init, derivedSystem, childrenSet)(dropFromActorMap(path, childrenSet))
    _ <- refActorMap.update(refMap => refMap + (path -> actor))
  } yield actor

  private[actors] def dropFromActorMap(path: ActorPath, childrenRef: Ref[Set[InternalActorCell[Nothing]]]): Task[Unit] =
    for {
      _        <- refActorMap.update(_ - path)
      children <- childrenRef.get
      _        <- ZIO.foreach_(children)(int => int.stop)
      _        <- childrenRef.set(Set.empty)
    } yield ()

  override def select[F](path: String): Task[ActorRef[F]] = {
    for {
      actorMap  <- refActorMap.get
      finalName <- buildActorName(path)
      actorRef  <- selectByActorPath[F](finalName, actorMap)
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
      case Some(value) => for { actor <- IO.effectTotal(value.asInstanceOf[ActorRef[F]]) } yield actor
      case None        => IO.fail(new Exception(s"No such actor $finalName in local ActorSystem."))
    }
  }

  private def selectByActorPathOption[F](finalName: ActorPath, actorMap: Map[ActorPath, Any]) = IO
    .effectTotal(actorMap.get(finalName).map(_.asInstanceOf[ActorRef[F]]))

  override def deadLetters: ActorRef[DeadLetter] = _deadLetters

  override def selectOption[F1](path: String): Task[Option[ActorRef[F1]]] = for {
    actorMap  <- refActorMap.get
    finalName <- buildActorName(path)
    result    <- selectByActorPathOption[F1](finalName, actorMap)
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
  def apply(sysName: String, debugActors: Boolean = false): ZManaged[Logging with Clock with Console, Throwable, ActorSystem] = {
    (
      for {
        initActorRefMap <- Ref.make(Map.empty[ActorPath, InternalActorCell[Nothing]])
        subscriptions <- Ref.make(Map.empty[Class[_], Set[ActorRef[Nothing]]])
        debugLoopEnabled <- Ref.make(debugActors)
        _ <- (for {
          actorMap <- initActorRefMap.get
          _ <- Logging.info(s"Actor system $sysName currently has ${actorMap.size} actors: \n${actorMap.values.map(_.actor).mkString("\n")}")
          _ <- ZIO.sleep(3.seconds)
        } yield ()).repeatWhileM(_ => debugLoopEnabled.get).onExit((exit: Exit[Any, Unit]) => Logging.info(s"Stopped actor system debug loop with $exit")).when(debugActors).fork
        eventStream = EventStream(subscriptions)
        deadLetters = DeadLetterActorRef(eventStream)
        supervisor <- Supervisor.track(true)
        actorSystem <- IO.effect(new ActorSystem(sysName, initActorRefMap, parentActor = None, eventStream, deadLetters, supervisor))
        deadLetterListener <- DeadLetterListener(actorSystem)
        _ <- eventStream.subscribe[DeadLetter](deadLetterListener)
      } yield (actorSystem, debugLoopEnabled)).toManaged(pair => for {
      _ <- pair._2.set(false)
      _ <- pair._1.shutdown()
    } yield ()).map(_._1)
  }
}
