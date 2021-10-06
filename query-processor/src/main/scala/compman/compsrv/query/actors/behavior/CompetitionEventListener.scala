package compman.compsrv.query.actors.behavior

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.{Mapping, Payload}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.query.model._
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.kafka.EventStreamingService.EventStreaming
import compman.compsrv.query.service.repository._
import io.getquill.CassandraZioSession
import zio.logging.Logging
import zio.{Fiber, RIO, Ref, Tag, ZIO}

object CompetitionEventListener {
  sealed trait ApiCommand[+_]
  case class EventReceived(event: EventDTO) extends ApiCommand[Unit]

  trait ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO]
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO]

  }

  case class Live(cassandraZioSession: CassandraZioSession) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic
      .logging.CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(cassandraZioSession)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
      .live(cassandraZioSession)
  }

  case class Test(
                   competitionProperties: Option[Ref[Map[String, CompetitionProperties]]] = None,
                   categories: Option[Ref[Map[String, Category]]] = None,
                   competitors: Option[Ref[Map[String, Competitor]]] = None,
                   fights: Option[Ref[Map[String, Fight]]] = None,
                   periods: Option[Ref[Map[String, Period]]] = None,
                   registrationPeriods: Option[Ref[Map[String, RegistrationPeriod]]] = None,
                   registrationGroups: Option[Ref[Map[String, RegistrationGroup]]] = None,
                   stages: Option[Ref[Map[String, StageDescriptor]]] = None
                 ) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic
      .logging.CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .test(competitionProperties, categories, competitors, fights, periods, registrationPeriods, registrationGroups, stages)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
      .test(competitionProperties, categories, competitors, fights, periods, registrationPeriods, registrationGroups, stages)
  }

  case class ActorState()

  val initialState: ActorState = ActorState()

  def behavior[R: Tag](
                        eventStreaming: EventStreaming[R],
                        topic: String,
                        context: ActorContext,
                        websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
                      ): ActorBehavior[R with Logging, ActorState, ApiCommand] = new ActorBehavior[R with Logging, ActorState, ApiCommand] {

    import context._
    import zio.interop.catz._
    override def receive[A](
      context: Context[ApiCommand],
      actorConfig: ActorConfig,
      state: ActorState,
      command: ApiCommand[A],
      timers: Timers[R with Logging, ApiCommand]
    ): RIO[R with Logging, (ActorState, A)] = {
      command match {
        case EventReceived(event) => for {
          mapped <- EventMapping.mapEventDto[LIO](event)
          _ <- EventProcessors.applyEvent[LIO, Payload](mapped)
          _ <- (websocketConnectionSupervisor ? WebsocketConnectionSupervisor.EventReceived(event))
            .onError(cause => Logging.error(s"Error while processing event $event", cause)).fork
          } yield (state, ().asInstanceOf[A])
      }
    }

    override def init(
      actorConfig: ActorConfig,
      context: Context[ApiCommand],
      initState: ActorState,
      timers: Timers[R with Logging, ApiCommand]
    ): RIO[R with Logging, (Seq[Fiber[Throwable, Unit]], Seq[ApiCommand[Any]])] = for {
      mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
      k <- (for {
        _ <- Logging.info(s"Starting stream for listening to competition events for topic: $topic")
        _ <- eventStreaming.getByteArrayStream(topic).mapM(record =>
          for {
            event <- ZIO.effect(mapper.readValue(record, classOf[EventDTO]))
            _     <- context.self ! EventReceived(event)
          } yield ()
        ).runDrain
        _ <- Logging.info(s"Finished stream for listening to competition events for topic: $topic")
      } yield ()).fork
    } yield (Seq(k), Seq.empty[ApiCommand[Any]])
  }
}
