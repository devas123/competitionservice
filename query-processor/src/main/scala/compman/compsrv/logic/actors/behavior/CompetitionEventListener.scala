package compman.compsrv.logic.actors.behavior

import cats.arrow.FunctionK
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, MessageReceived}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.CompetitionEventListenerSupervisor.CompetitionUpdated
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.{logError, LIO}
import compman.compsrv.model
import compman.compsrv.model.{Mapping, Payload}
import compman.compsrv.model.Mapping.EventMapping
import compman.compsrv.model.event.Events.CompetitionPropertiesUpdatedEvent
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.CompetitionPropertiesUpdatedPayload
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.service.event.EventProcessors
import compman.compsrv.query.service.repository._
import org.mongodb.scala.MongoClient
import zio.{Cause, Fiber, Queue, Ref, RIO, Tag, ZIO}
import zio.clock.Clock
import zio.kafka.consumer.Offset
import zio.logging.Logging

object CompetitionEventListener {
  sealed trait ApiCommand[+_]
  case class EventReceived(kafkaMessage: KafkaConsumerApi[Any]) extends ApiCommand[Unit]
  case class CommitOffset(offset: Offset)                       extends ApiCommand[Unit]
  case class SetQueue(queue: Queue[Offset])                     extends ApiCommand[Unit]
  case object Stop                                              extends ApiCommand[Unit]

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
    new ActorBehavior[R with Logging with Clock, ActorState, ApiCommand] {
      import context._
      import zio.interop.catz._
      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R with Logging with Clock, ApiCommand]
      ): RIO[R with Logging with Clock, (ActorState, A)] = {
        command match {
          case EventReceived(kafkaMessage) => kafkaMessage match {
              case KafkaSupervisor.QueryStarted() => Logging.info("Kafka query started.")
                  .as((state, ().asInstanceOf[A]))
              case KafkaSupervisor.QueryFinished() => Logging.info("Kafka query finished.")
                  .as((state, ().asInstanceOf[A]))
              case KafkaSupervisor.QueryError(error) => Logging.error("Error during kafka query: ", Cause.fail(error))
                  .as((state, ().asInstanceOf[A]))
              case MessageReceived(topic, record) => {
                  for {
                    event  <- ZIO.effect(mapper.readValue(record.value, classOf[EventDTO]))
                    mapped <- EventMapping.mapEventDto[LIO](event)
                    _      <- EventProcessors.applyEvent[LIO, Payload](mapped)
                    _ <-
                      if (mapped.isInstanceOf[CompetitionPropertiesUpdatedEvent]) {
                        competitionEventListenerSupervisor !
                          CompetitionUpdated(event.getPayload.asInstanceOf[CompetitionPropertiesUpdatedPayload], topic)
                      } else { ZIO.unit }
                    _ <- (websocketConnectionSupervisor ! WebsocketConnectionSupervisor.EventReceived(event)).fork
                    _ <- context.self ! CommitOffset(record.offset)
                  } yield (state, ().asInstanceOf[A])
                }.onError(cause => logError(cause.squash))
            }

          case Stop => context.stopSelf.map(_ => (state, ().asInstanceOf[A]))
          case CommitOffset(offset) => state.queue.map(_.offer(offset)).getOrElse(RIO.unit)
              .map(_ => (state, ().asInstanceOf[A]))
          case SetQueue(queue) => Logging.info("Setting queue.") *>
              ZIO.effectTotal((state.copy(queue = Some(queue)), ().asInstanceOf[A]))
        }
      }

      override def init(
        actorConfig: ActorConfig,
        context: Context[ApiCommand],
        initState: ActorState,
        timers: Timers[R with Logging with Clock, ApiCommand]
      ): RIO[R with Logging with Clock, (Seq[Fiber[Throwable, Unit]], Seq[ApiCommand[Any]])] = for {
        adapter <- context.messageAdapter(new FunctionK[KafkaConsumerApi, ApiCommand] {
          override def apply[A](fa: KafkaConsumerApi[A]): ApiCommand[A] = {
            EventReceived(fa).asInstanceOf[ApiCommand[A]]
          }
        })
        groupId = s"query-service-$competitionId"
        _ <- kafkaSupervisorActor ! KafkaSupervisor.QueryAndSubscribe(topic, groupId, adapter)
      } yield (Seq(), Seq.empty[ApiCommand[Any]])
    }
  }
}
