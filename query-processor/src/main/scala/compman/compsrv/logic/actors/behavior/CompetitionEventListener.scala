package compman.compsrv.logic.actors.behavior

import cats.implicits.catsSyntaxApplicativeError
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{
  KafkaConsumerApi,
  KafkaSupervisorCommand,
  MessageReceived,
  PublishMessage
}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.behavior.CompetitionEventListenerSupervisor.{
  CompetitionDeletedMessage,
  CompetitionUpdated
}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model
import compman.compsrv.model.{Errors, Mapping}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.command.Commands
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events.{
  CompetitionCreatedEvent,
  CompetitionDeletedEvent,
  CompetitionPropertiesUpdatedEvent
}
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.repository._
import compman.compsrv.query.service.repository.EventOffsetOperations.EventOffsetService
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload
import org.mongodb.scala.MongoClient
import zio.{Cause, Queue, Ref, RIO, Tag, Task, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.kafka.consumer.Offset
import zio.logging.Logging

object CompetitionEventListener {
  sealed trait ApiCommand
  case class KafkaMessageReceived(kafkaMessage: KafkaConsumerApi) extends ApiCommand
  case class CommitOffset(offset: Offset)                         extends ApiCommand
  case class SetQueue(queue: Queue[Offset])                       extends ApiCommand
  case object Stop                                                extends ApiCommand

  trait ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO]
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO]
    implicit val fightQueryOperations: FightQueryOperations[LIO]
    implicit val fightUpdateOperations: FightUpdateOperations[LIO]
    implicit val eventOffsetService: EventOffsetService[LIO]
  }

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO] = model.Mapping.EventMapping.live
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic.logging.CompetitionLogging.Live
      .live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightQueryOperations: FightQueryOperations[LIO] = FightQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightUpdateOperations: FightUpdateOperations[LIO] = FightUpdateOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val eventOffsetService: EventOffsetService[LIO] = EventOffsetOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
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
    implicit val loggingLive: CompetitionLogging.Service[LIO] = compman.compsrv.logic.logging.CompetitionLogging.Live
      .live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .test(competitionProperties, categories, competitors, periods, registrationPeriods, registrationGroups, stages)
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO] = CompetitionUpdateOperations
      .test(competitionProperties, categories, competitors, periods, registrationPeriods, registrationGroups, stages)
    implicit val fightUpdateOperations: FightUpdateOperations[LIO] = FightUpdateOperations.test(fights)
    implicit val fightQueryOperations: FightQueryOperations[LIO]   = FightQueryOperations.test(fights, stages)
    implicit val eventOffsetService: EventOffsetService[LIO]       = EventOffsetOperations.test
  }

  private[behavior] case class ActorState(queue: Option[Queue[Offset]] = None)

  val initialState: ActorState = ActorState()

  def behavior[R: Tag](
    competitionId: String,
    topic: String,
    callbackTopic: String,
    context: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    competitionEventListenerSupervisor: ActorRef[CompetitionEventListenerSupervisor.ActorMessages],
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): ActorBehavior[R with Logging with Clock with Console, ActorState, ApiCommand] = {
    def notifyEventListenerSupervisor(topic: String, event: Event, mapped: Events.Event[Any]): Task[Unit] = {
      mapped match {
        case CompetitionCreatedEvent(_, _, _, _) => for {
            payload <- ZIO.fromOption(event.messageInfo.flatMap(_.payload.competitionCreatedPayload))
              .orElseFail(new Exception("No Payload"))
            _ <- competitionEventListenerSupervisor ! CompetitionUpdated(
              CompetitionPropertiesUpdatedPayload().update(_.properties.setIfDefined(payload.properties)),
              topic
            )
          } yield ()
        case _: CompetitionPropertiesUpdatedEvent => for {
            payload <- ZIO.fromOption(event.messageInfo.flatMap(_.payload.competitionPropertiesUpdatedPayload))
              .orElseFail(new Exception("No Payload"))
            _ <- competitionEventListenerSupervisor ! CompetitionUpdated(payload, topic)
          } yield ()
        case CompetitionDeletedEvent(competitionId, _) => for {
            id <- ZIO.fromOption(competitionId).orElseFail(new Exception("No Competition ID"))
            _  <- competitionEventListenerSupervisor ! CompetitionDeletedMessage(id)
          } yield ()
        case _ => ZIO.unit
      }
    }

    import Behaviors._
    import context._
    import zio.interop.catz._

    def sendSuccessfulExecutionCallbacks(event: Event) = {
      for {
        _ <- websocketConnectionSupervisor ! WebsocketConnectionSupervisor.EventReceived(event)
        _ <- kafkaSupervisorActor ! PublishMessage(Commands.createSuccessCallbackMessageParameters(
          callbackTopic,
          Commands.correlationId(event).get,
          event.numberOfEventsInBatch
        ))
      } yield ()
    }

    def sendErrorCallback(event: Event, value: Throwable) = {
      kafkaSupervisorActor ! PublishMessage(Commands.createErrorCommandCallbackMessageParameters(
        callbackTopic,
        Commands.correlationId(event),
        Errors.InternalException(value)
      ))
    }

    Behaviors.behavior[R with Logging with Clock with Console, ActorState, ApiCommand].withReceive {
      (context, _, state, command, _) =>
        {
          command match {
            case KafkaMessageReceived(kafkaMessage) => kafkaMessage match {
                case KafkaSupervisor.QueryStarted()   => Logging.info("Kafka query started.").as(state)
                case KafkaSupervisor.QueryFinished(_) => Logging.info("Kafka query finished.").as(state)
                case KafkaSupervisor.QueryError(error) => Logging.error("Error during kafka query: ", Cause.fail(error))
                    .as(state)
                case MessageReceived(topic, record) => {
                    for {
                      event  <- ZIO.effect(Event.parseFrom(record.value))
                      mapped <- EventMapping.mapEventDto[LIO](event)
                      _      <- Logging.info(s"Received event: $mapped")
                      res    <- EventProcessors.applyEvent[LIO](mapped).attempt
                      _      <- EventOffsetOperations.setOffset[LIO](EventOffset(topic, event.version.toLong))
                      _      <- notifyEventListenerSupervisor(topic, event, mapped)
                      _ <- res match {
                        case Left(value) => sendErrorCallback(event, value)
                        case Right(_) => Logging.info(
                            s"Sending callback, correlation ID is ${event.messageInfo.flatMap(_.correlationId)}"
                          ) *> sendSuccessfulExecutionCallbacks(event)
                            .when(event.localEventNumber == event.numberOfEventsInBatch - 1)
                      }
                      _ <- context.self ! CommitOffset(record.offset)
                    } yield state
                  }.onError(cause => logError(cause.squash))
              }

            case Stop => Logging.info("Received stop command. Stopping...") *> context.stopSelf.as(state)
            case CommitOffset(offset) => state.queue.map(_.offer(offset)).getOrElse(RIO.unit).as(state)
            case SetQueue(queue) => Logging.info("Setting queue.") *> ZIO.effectTotal(state.copy(queue = Some(queue)))
          }
        }
    }.withInit { (_, context, initState, _) =>
      for {
        adapter <- context.messageAdapter[KafkaConsumerApi](fa => Some(KafkaMessageReceived(fa)))
        groupId = s"query-service-$competitionId"
        _ <- kafkaSupervisorActor ! KafkaSupervisor.Subscribe(topic, groupId, adapter)
      } yield (Seq(), Seq.empty[ApiCommand], initState)
    }
  }
}
