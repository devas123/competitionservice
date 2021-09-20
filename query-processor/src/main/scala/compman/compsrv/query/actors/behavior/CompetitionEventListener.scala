package compman.compsrv.query.actors.behavior

import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model
import compman.compsrv.model.{Mapping, Payload}
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import compman.compsrv.query.service.repository._
import zio.{Fiber, RIO, Tag, ZIO}

object CompetitionEventListener {
  sealed trait ApiCommand[+_]
  case class EventReceived(event: EventDTO) extends ApiCommand[Unit]

  trait ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO]
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[RepoIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[RepoIO]
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[RepoIO]

  }

  object Live extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[RepoIO] = compman.compsrv.logic
      .logging.CompetitionLogging.Live.live[QuillCassandraEnvironment]
    implicit val competitionQueryOperations: CompetitionQueryOperations[RepoIO]   = CompetitionQueryOperations.live
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[RepoIO] = CompetitionUpdateOperations.live
  }

  case class ActorState()
  val initialState: ActorState = ActorState()
  def behavior[R: Tag](
    eventStreaming: EventStreaming[R],
    topic: String,
    context: ActorContext
  ): ActorBehavior[R with RepoEnvironment, ActorState, ApiCommand] =
    new ActorBehavior[R with RepoEnvironment, ActorState, ApiCommand] {
      import context._
      import zio.interop.catz._
      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R with RepoEnvironment, ApiCommand]
      ): RIO[R with RepoEnvironment, (ActorState, A)] = {
        command match {
          case EventReceived(event) => for {
              mapped <- EventMapping.mapEventDto[LIO](event)
              _      <- EventProcessors.applyEvent[RepoIO, Payload](mapped)
            } yield (state, ().asInstanceOf[A])
        }
      }

      override def init(
        actorConfig: ActorConfig,
        context: Context[ApiCommand],
        initState: ActorState,
        timers: Timers[R with RepoEnvironment, ApiCommand]
      ): RIO[R with RepoEnvironment, (Seq[Fiber[Throwable, Unit]], Seq[ApiCommand[Any]])] = for {
        mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
        k <- eventStreaming.getByteArrayStream(topic).mapM(record =>
          for {
            event <- ZIO.effect(mapper.readValue(record, classOf[EventDTO]))
            _     <- context.self ! EventReceived(event)
          } yield ()
        ).runDrain.fork
      } yield (Seq(k), Seq.empty[ApiCommand[Any]])
    }
}
