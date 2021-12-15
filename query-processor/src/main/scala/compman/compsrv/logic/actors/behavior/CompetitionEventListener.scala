package compman.compsrv.logic.actors.behavior

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, MessageReceived}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors._
import compman.compsrv.logic.actors.behavior.CompetitionEventListenerSupervisor.{
  CompetitionDeletedMessage,
  CompetitionUpdated
}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model
import compman.compsrv.model.{Mapping, Payload}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.event.Events
import compman.compsrv.model.event.Events.{CompetitionDeletedEvent, CompetitionPropertiesUpdatedEvent}
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.CompetitionPropertiesUpdatedPayload
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.repository._
import org.mongodb.scala.MongoClient
import zio.{Cause, Queue, Ref, RIO, Tag, ZIO}
import zio.clock.Clock
import zio.kafka.consumer.Offset
import zio.logging.Logging

object CompetitionEventListener {
  sealed trait ApiCommand
  case class EventReceived(kafkaMessage: KafkaConsumerApi) extends ApiCommand
  case class CommitOffset(offset: Offset)                  extends ApiCommand
  case class SetQueue(queue: Queue[Offset])                extends ApiCommand
  case object Stop                                         extends ApiCommand

  trait ActorContext {
    implicit val eventMapping: Mapping.EventMapping[LIO]
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
    implicit val competitionUpdateOperations: CompetitionUpdateOperations[LIO]
    implicit val fightQueryOperations: FightQueryOperations[LIO]
    implicit val fightUpdateOperations: FightUpdateOperations[LIO]
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
  }

  private[behavior] case class ActorState(queue: Option[Queue[Offset]] = None)

  val initialState: ActorState = ActorState()

  def behavior[R: Tag](
    mapper: ObjectMapper,
    competitionId: String,
    topic: String,
    context: ActorContext,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    competitionEventListenerSupervisor: ActorRef[CompetitionEventListenerSupervisor.ActorMessages],
    websocketConnectionSupervisor: ActorRef[WebsocketConnectionSupervisor.ApiCommand]
  ): ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] = {
    def notifyEventListenerSupervisor[A](topic: String, event: EventDTO, mapped: Events.Event[Payload]) = {
      mapped match {
        case CompetitionPropertiesUpdatedEvent(_, _, _) => competitionEventListenerSupervisor !
            CompetitionUpdated(event.getPayload.asInstanceOf[CompetitionPropertiesUpdatedPayload], topic)
        case CompetitionDeletedEvent(competitionId, _) => competitionId
            .map(id => competitionEventListenerSupervisor ! CompetitionDeletedMessage(id)).getOrElse(ZIO.unit)
        case _ => ZIO.unit
      }
    }

    import Behaviors._
    import context._
    import zio.interop.catz._

    Behaviors.behavior[R with Logging with Clock, ActorState, ApiCommand].withReceive {
      (context, _, state, command, _) =>
        {
          command match {
            case EventReceived(kafkaMessage) => kafkaMessage match {
                case KafkaSupervisor.QueryStarted()  => Logging.info("Kafka query started.").as(state)
                case KafkaSupervisor.QueryFinished() => Logging.info("Kafka query finished.").as(state)
                case KafkaSupervisor.QueryError(error) => Logging.error("Error during kafka query: ", Cause.fail(error))
                    .as(state)
                case MessageReceived(topic, record) => {
                    for {
                      event  <- ZIO.effect(mapper.readValue(record.value, classOf[EventDTO]))
                      mapped <- EventMapping.mapEventDto[LIO](event)
                      _      <- Logging.info(s"Received event: $mapped")
                      _      <- EventProcessors.applyEvent[LIO, Payload](mapped)
                      _      <- notifyEventListenerSupervisor(topic, event, mapped)
                      _ <- (websocketConnectionSupervisor ! WebsocketConnectionSupervisor.EventReceived(event)).fork
                      _ <- context.self ! CommitOffset(record.offset)
                    } yield state
                  }.onError(cause => logError(cause.squash))
              }

            case Stop                 => Logging.info("Received stop command. Stopping...") *> context.stopSelf.as(state)
            case CommitOffset(offset) => state.queue.map(_.offer(offset)).getOrElse(RIO.unit).as(state)
            case SetQueue(queue) => Logging.info("Setting queue.") *> ZIO.effectTotal(state.copy(queue = Some(queue)))
          }
        }
    }.withInit { (_, context, initState, _) =>
      for {
        adapter <- context.messageAdapter[KafkaConsumerApi](fa => EventReceived(fa))
        groupId = s"query-service-$competitionId"
        _ <- kafkaSupervisorActor ! KafkaSupervisor.QueryAndSubscribe(topic, groupId, adapter)
      } yield (Seq(), Seq.empty[ApiCommand], initState)
    }
  }
}
