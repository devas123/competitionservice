package compman.compsrv.query.actors

import compman.compsrv.query.actors.CompetitionProcessorActor.Context
import compman.compsrv.query.model.CompetitionInfoTemplate
import zio.{IO, Promise, Ref, RIO, Task, UIO, ZIO}
import zio.clock.Clock

object CompetitionApiActor {

  private val DefaultActorMailboxSize: Int = 100
  case class ActorConfig(mailboxSize: Int = DefaultActorMailboxSize)
  private[actors] type PendingMessage[F[_], A] = (F[A], Promise[Throwable, A])

  sealed trait ApiCommand[+_]
  final case object GetCompetitionInfoTemplate extends ApiCommand[CompetitionInfoTemplate]
  case class ActorState()
  val initialState: ActorState = ActorState()
  val behavior: ActorBehavior[Any, ActorState, ApiCommand] = new ActorBehavior[Any, ActorState, ApiCommand] {
    override def receive[A](context: Context, actorConfig: ActorConfig, state: ActorState, command: ApiCommand[A], timers: Timers[Any, ApiCommand]): RIO[Any, (ActorState, A)] = ???

    override def init(context: Context, actorConfig: ActorConfig, initState: ActorState, timers: Timers[Any, ApiCommand]): RIO[Any, Unit] = RIO.unit
  }

  private[actors] final class ActorSystem(
    private[actors] val actorSystemName: String,
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
      behavior: ActorBehavior[R, S, F]
    ): RIO[R with Clock, CompetitionProcessorActorRef[F]] = for {
      map       <- refActorMap.get
      finalName <- buildFinalName(parentActor.getOrElse(""), actorName)
      _         <- if (map.contains(finalName)) IO.fail(new Exception(s"Actor $finalName already exists")) else IO.unit
      derivedSystem = new ActorSystem(actorSystemName, refActorMap, Some(finalName))
      childrenSet <- Ref.make(Map.empty[String, CompetitionProcessorActorRef[Any]])
      actor <- behavior.makeActor[R](actorConfig, init, Context(childrenSet, finalName, derivedSystem))(() =>
        dropFromActorMap(finalName, childrenSet)
      )
      _ <- refActorMap.set(map + (finalName -> actor))
    } yield actor

    private[actors] def dropFromActorMap(
      path: String,
      childrenRef: Ref[Map[String, CompetitionProcessorActorRef[Any]]]
    ): Task[Unit] = for {
      _        <- refActorMap.update(_ - path)
      children <- childrenRef.get
      _        <- ZIO.foreach_(children)(_._2.stop)
      _        <- childrenRef.set(Map.empty)
    } yield ()

  }
  object ActorSystem {

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
}
