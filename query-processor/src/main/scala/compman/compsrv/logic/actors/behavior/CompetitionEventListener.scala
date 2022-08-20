package compman.compsrv.logic.actors.behavior

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import cats.implicits._
import compman.compsrv.logic.actor.kafka.{KafkaConsumerApi, KafkaSupervisorCommand}
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi._
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.{PublishMessage, Subscribe}
import compman.compsrv.model
import compman.compsrv.model.{Errors, Mapping}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.command.Commands
import compman.compsrv.model.event.Events.CompetitionDeletedEvent
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.repository._
import compman.compsrv.query.service.repository.BlobOperations.BlobService
import compman.compsrv.query.service.repository.EventOffsetOperations.EventOffsetService
import compservice.model.protobuf.event.Event
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
    implicit val blobOperations: BlobService[IO]
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
    override implicit val blobOperations: BlobService[IO] = BlobOperations
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
    override implicit val blobOperations: BlobService[IO]         = BlobOperations.test[IO]
  }

  def behavior(
    competitionId: String,
    topic: String,
    callbackTopic: String,
    context: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): Behavior[ApiCommand] = {
    import context._

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
      ctx.log.info(s"Starting competition processing for competition: $competitionId, from topic: $topic.")
      ctx.log.info(s"Will send callbacks to $callbackTopic")
      val adapter = ctx.messageAdapter[KafkaConsumerApi](fa => KafkaMessageReceived(fa))
      ctx.pipeToSelf(EventOffsetOperations.getOffset[IO](topic).unsafeToFuture()) {
        case Failure(exception) => Stop(exception.getMessage, Some(exception))
        case Success(value)     => OffsetReceived(value.getOrElse(EventOffset(topic, 0)))
      }

      val groupId = s"query-service-$competitionId"
      Behaviors.receiveMessage {
        case OffsetReceived(offset) =>
          kafkaSupervisorActor !
            Subscribe(topic, groupId, adapter, startOffsets = Map(0 -> offset.offset).withDefault(_ => offset.offset))
          Behaviors.same
        case KafkaMessageReceived(kafkaMessage) =>
          ctx.log.info(s"Received message from kafka: $kafkaMessage")
          kafkaMessage match {
            case QueryStarted() =>
              ctx.log.info("Kafka query started.")
              Behaviors.same
            case QueryFinished(_) =>
              ctx.log.info("Kafka query finished.")
              Behaviors.same
            case QueryError(error) =>
              ctx.log.error("Error during kafka query: ", error)
              Behaviors.same

            case MessageReceived(topic, record) =>
              val event = Event.parseFrom(record.value)
              ctx.log.info(s"Received event: $event")
              (for {
                mapped <- EventMapping.mapEventDto[IO](event)
                res    <- EventProcessors.applyEvent[IO](mapped).attempt
                _ <- IO {
                  res match {
                    case Left(value) => sendErrorCallback(event, value)
                    case Right(_) => if (event.localEventNumber == event.numberOfEventsInBatch - 1) {
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
