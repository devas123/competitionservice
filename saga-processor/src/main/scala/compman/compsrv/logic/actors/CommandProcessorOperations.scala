package compman.compsrv.logic.actors

import compman.compsrv.jackson.{ObjectMapperFactory, SerdeApi}
import compman.compsrv.logic.actors.CommandProcessorOperations.KafkaTopicConfig
import compman.compsrv.logic.actors.CompetitionProcessorActor.LiveEnv
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.{CompetitionState, CompetitionStateImpl}
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, Queue, RIO, Ref, URIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.admin.AdminClient
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.serde.Serde
import zio.logging.Logging

import java.time.Instant

trait CommandProcessorOperations[-E] {
  def retrieveEvents(id: String, offset: Long): RIO[E, List[EventDTO]]

  def persistEvents(events: Seq[EventDTO]): RIO[E, Unit]

  def sendNotifications(notifications: Seq[CommandProcessorNotification]): RIO[E, Unit]

  def createTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig): RIO[E, Unit]

  def getStateSnapshot(id: String): URIO[E with SnapshotService.Snapshot, Option[CompetitionState]]

  def saveStateSnapshot(state: CompetitionState): URIO[E with SnapshotService.Snapshot, Unit]

  def createInitialState(config: ActorConfig): LIO[CompetitionState] = RIO {
    CompetitionStateImpl(
      id = config.competitionId,
      competitors = Option(Map.empty),
      competitionProperties = Option(
        new CompetitionPropertiesDTO().setId(config.competitionId).setStatus(CompetitionStatus.CREATED)
          .setCreationTimestamp(Instant.now()).setBracketsPublished(false).setSchedulePublished(false)
          .setStaffIds(Array.empty).setEmailNotificationsEnabled(false).setTimeZone("UTC")
      ),
      stages = Some(Map.empty),
      fights = Some(Map.empty),
      categories = Some(Map.empty),
      registrationInfo = Some(
        new RegistrationInfoDTO().setId(config.competitionId).setRegistrationGroups(Array.empty)
          .setRegistrationPeriods(Array.empty).setRegistrationOpen(false)
      ),
      schedule = Some(new ScheduleDTO().setId(config.competitionId).setMats(Array.empty).setPeriods(Array.empty)),
      revision = 0L
    )
  }

}

object CommandProcessorOperations {
  case class KafkaTopicConfig(numPartitions: Int = 1, replicationFactor: Short = 1, additionalProperties: Map[String, String] = Map.empty)

  def apply[E](adminClient: AdminClient): RIO[E with LiveEnv, CommandProcessorOperations[E with LiveEnv]] = {
    for {
      mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
      operations = new CommandProcessorOperations[E with LiveEnv] {
        override def retrieveEvents(id: String, offset: Long): RIO[E with LiveEnv, List[EventDTO]] = Consumer
          .subscribeAnd(Subscription.topics(id))
          .plainStream(Serde.string, SerdeApi.eventDeserializer)
          .filter(_.offset.offset >= offset)
          .runCollect
          .map(_.map(_.value).toList)

        override def persistEvents(events: Seq[EventDTO]): RIO[E with LiveEnv, Unit] = {
          zio.kafka.producer.Producer.produceChunk[Any, String, Array[Byte]](Chunk.fromIterable(events).map(e =>
            new ProducerRecord[String, Array[Byte]](e.getCompetitionId, mapper.writeValueAsBytes(e))
          )).ignore
        }

        override def getStateSnapshot(id: String): URIO[E with LiveEnv, Option[CompetitionState]] = SnapshotService.load(id)

        override def saveStateSnapshot(state: CompetitionState): URIO[E with LiveEnv, Unit] = SnapshotService.save(state)

        override def sendNotifications(notifications: Seq[CommandProcessorNotification]): RIO[E with LiveEnv, Unit] = {
          zio.kafka.producer.Producer.produceChunk[Any, String, Array[Byte]](Chunk.fromIterable(notifications).map(e =>
            new ProducerRecord[String, Array[Byte]](e.competitionId.get, mapper.writeValueAsBytes(e))
          )).ignore
        }

        override def createTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig): RIO[E with LiveEnv, Unit] = for {
          topics <- adminClient.listTopics()
          _ <- if (topics.contains(topic)) RIO(()) else adminClient.createTopic(AdminClient.NewTopic(topic,
            topicConfig.numPartitions, topicConfig.replicationFactor))
        } yield ()
      }
    } yield operations
  }

  def test[Env](
                 eventReceiver: Queue[EventDTO],
                 notificationReceiver: Queue[CommandProcessorNotification],
                 stateSnapshots: Ref[Map[String, CompetitionState]],
                 initialState: Option[CompetitionState] = None
  ): CommandProcessorOperations[Env with Clock with Blocking with Logging] = {
    new CommandProcessorOperations[Env with Clock with Blocking with Logging] {
      self =>
      override def retrieveEvents(id: String, offset: Long): RIO[Env with Clock with Blocking with Logging, List[EventDTO]] = RIO.effectTotal(List.empty)
      override def persistEvents(events: Seq[EventDTO]): RIO[Env with Clock with Blocking with Logging, Unit] = for {
        _ <- eventReceiver.offerAll(events)
      } yield ()

      override def createInitialState(config: ActorConfig): LIO[CompetitionState] = {
        initialState.map(RIO.effectTotal(_)).getOrElse(super.createInitialState(config))
      }

      override def getStateSnapshot(id: String): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Option[CompetitionState]] =
        for {map <- stateSnapshots.get} yield map.get(id)

      override def saveStateSnapshot(state: CompetitionState): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Unit] = for {
        _ <- stateSnapshots.update(_ + (state.id -> state))
      } yield ()

      override def sendNotifications(notifications: Seq[CommandProcessorNotification]): RIO[Env with Clock with Blocking with Logging, Unit] = notificationReceiver.offerAll(notifications).ignore

      override def createTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig): RIO[Env with Clock with Blocking with Logging, Unit] = RIO(())
    }
  }

}
