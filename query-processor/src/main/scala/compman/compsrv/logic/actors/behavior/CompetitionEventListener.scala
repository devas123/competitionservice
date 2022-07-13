package compman.compsrv.logic.actors.behavior

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import cats.implicits._
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, MessageReceived, PublishMessage}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors.behavior.CompetitionEventListenerSupervisor.{CompetitionDeletedMessage, CompetitionUpdated}
import compman.compsrv.model
import compman.compsrv.model.{Errors, Mapping}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.command.Commands
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events.{CompetitionCreatedEvent, CompetitionDeletedEvent, CompetitionPropertiesUpdatedEvent}
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.repository._
import compman.compsrv.query.service.repository.EventOffsetOperations.EventOffsetService
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload
import org.mongodb.scala.MongoClient

import java.util.concurrent.atomic.AtomicReference
import scala.util.{Failure, Success}

object CompetitionEventListener {
  sealed trait ApiCommand
  case class KafkaMessageReceived(kafkaMessage: KafkaConsumerApi)      extends ApiCommand
  case class OffsetReceived(offset: EventOffset)                       extends ApiCommand
  case class Stop(reason: String, throwable: Option[Throwable] = None) extends ApiCommand

  trait ActorContext extends WithIORuntime {
    implicit val eventMapping: Mapping.EventMapping[IO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO]
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[IO]
    implicit val fightQueryOperations: FightQueryOperations[IO]
    implicit val fightUpdateOperations: FightUpdateOperations[IO]
    implicit val eventOffsetService: EventOffsetService[IO]
  }

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[IO] = model.Mapping.EventMapping.live
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO] = CompetitionQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[IO] = CompetitionUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightQueryOperations: FightQueryOperations[IO] = FightQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightUpdateOperations: FightUpdateOperations[IO] = FightUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val eventOffsetService: EventOffsetService[IO] = EventOffsetOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  case class Test(
    competitionProperties: Option[AtomicReference[Map[String, CompetitionProperties]]] = None,
    categories: Option[AtomicReference[Map[String, Category]]] = None,
    competitors: Option[AtomicReference[Map[String, Competitor]]] = None,
    fights: Option[AtomicReference[Map[String, Fight]]] = None,
    periods: Option[AtomicReference[Map[String, Period]]] = None,
    registrationInfo: Option[AtomicReference[Map[String, RegistrationInfo]]] = None,
    stages: Option[AtomicReference[Map[String, StageDescriptor]]] = None
  ) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[IO] = model.Mapping.EventMapping.live
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO] = CompetitionQueryOperations
      .test(competitionProperties, registrationInfo, categories, competitors, periods, stages)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[IO] = CompetitionUpdateOperations
      .test(competitionProperties, registrationInfo, categories, competitors, periods, stages)
    implicit val fightUpdateOperations: FightUpdateOperations[IO] = FightUpdateOperations.test(fights)
    implicit val fightQueryOperations: FightQueryOperations[IO]   = FightQueryOperations.test(fights, stages)
    implicit val eventOffsetService: EventOffsetService[IO]       = EventOffsetOperations.test
  }

  private[behavior] case class ActorState()

  val initialState: ActorState = ActorState()

  def behavior(
    competitionId: String,
    topic: String,
    callbackTopic: String,
    context: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    competitionEventListenerSupervisor: ActorRef[CompetitionEventListenerSupervisor.ActorMessages],
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): Behavior[ApiCommand] = {
    import context._
    def notifyEventListenerSupervisor(topic: String, event: Event, mapped: Events.Event[Any]): IO[Unit] = {
      mapped match {
        case CompetitionCreatedEvent(_, _, _, _) => for {
            payload <- IO
              .fromOption(event.messageInfo.flatMap(_.payload.competitionCreatedPayload))(new Exception("No Payload"))
            _ <- IO {
              competitionEventListenerSupervisor ! CompetitionUpdated(
                CompetitionPropertiesUpdatedPayload().update(_.properties.setIfDefined(payload.properties)),
                topic
              )
            }
          } yield ()
        case _: CompetitionPropertiesUpdatedEvent => for {
            payload <- IO
              .fromOption(event.messageInfo.flatMap(_.payload.competitionPropertiesUpdatedPayload))(new Exception(
                "No Payload"
              ))
            _ <- IO { competitionEventListenerSupervisor ! CompetitionUpdated(payload, topic) }
          } yield ()
        case CompetitionDeletedEvent(competitionId, _) => for {
            id <- IO.fromOption(competitionId)(new Exception("No Competition ID"))
            _  <- IO { competitionEventListenerSupervisor ! CompetitionDeletedMessage(id) }
          } yield ()
        case _ => IO.unit
      }
    }

    def sendSuccessfulExecutionCallbacks(event: Event): Unit = {
      kafkaSupervisorActor ! PublishMessage(Commands.createSuccessCallbackMessageParameters(
        callbackTopic,
        Commands.correlationId(event).get,
        event.numberOfEventsInBatch
      ))
      websocketConnectionSupervisor ! WebsocketConnectionSupervisor.EventReceived(event)
    }

    def sendErrorCallback(event: Event, value: Throwable): Unit = {
      kafkaSupervisorActor ! PublishMessage(Commands.createErrorCommandCallbackMessageParameters(
        callbackTopic,
        Commands.correlationId(event),
        Errors.InternalException(value)
      ))
    }

    Behaviors.setup[ApiCommand] { ctx =>
      val adapter = ctx.messageAdapter[KafkaConsumerApi](fa => KafkaMessageReceived(fa))
      ctx.pipeToSelf(EventOffsetOperations.getOffset[IO](topic).unsafeToFuture()) {
        case Failure(exception) => Stop(exception.getMessage, Some(exception))
        case Success(value)     => OffsetReceived(value.getOrElse(EventOffset(topic, 0)))
      }

      val groupId = s"query-service-$competitionId"
      Behaviors.receiveMessage {
        case OffsetReceived(offset) =>
          kafkaSupervisorActor !
            KafkaSupervisor.QueryAndSubscribe(topic, groupId, adapter, startOffset = Some(offset.offset))
          Behaviors.same
        case KafkaMessageReceived(kafkaMessage) => kafkaMessage match {
            case KafkaSupervisor.QueryStarted() =>
              ctx.log.info("Kafka query started.")
              Behaviors.same
            case KafkaSupervisor.QueryFinished(_) =>
              ctx.log.info("Kafka query finished.")
              Behaviors.same
            case KafkaSupervisor.QueryError(error) =>
              ctx.log.error("Error during kafka query: ", error)
              Behaviors.same

            case MessageReceived(topic, record) =>
              (for {
                event  <- IO(Event.parseFrom(record.value))
                mapped <- EventMapping.mapEventDto[IO](event)
                _      <- IO(ctx.log.info(s"Received event: $mapped"))
                res    <- EventProcessors.applyEvent[IO](mapped).attempt
                _      <- notifyEventListenerSupervisor(topic, event, mapped)
                _ <- IO {
                  res match {
                    case Left(value) => sendErrorCallback(event, value)
                    case Right(_) =>
                      ctx.log.info(s"Sending callback, correlation ID is ${event.messageInfo.flatMap(_.correlationId)}")
                      if (event.localEventNumber == event.numberOfEventsInBatch - 1) {
                        sendSuccessfulExecutionCallbacks(event)
                      }
                  }
                }
                competitionDeleted = mapped.isInstanceOf[CompetitionDeletedEvent]
                _ <-
                  if (competitionDeleted) EventOffsetOperations.deleteOffset[IO](topic)
                  else EventOffsetOperations.setOffset[IO](EventOffset(topic, record.offset))
                _ <- IO(ctx.self ! Stop("Competition was deleted. Stopping")).whenA(competitionDeleted)
              } yield ()).unsafeRunSync()
              Behaviors.same
          }
        case Stop(reason, throwable) => Behaviors.stopped { () =>
            ctx.log.info(s"Received stop command. Stopping because $reason")
            throwable.foreach(t => ctx.log.error(s"Received an exception with the stop command:", t))
          }
      }
    }
  }
}
