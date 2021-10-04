package compman.compsrv.logic.actors

import compman.compsrv.jackson.{ObjectMapperFactory, SerdeApi}
import compman.compsrv.logic.actors.CommandProcessorOperations.KafkaTopicConfig
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.competition.{CompetitionPropertiesDTO, CompetitionStatus, RegistrationInfoDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.{CommandProcessorNotification, CompetitionState, CompetitionStateImpl}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.admin.AdminClient
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.{Chunk, Queue, RIO, Ref, URIO, ZIO}

import java.time.Instant

trait CommandProcessorOperations[-E] {
  def retrieveEvents(id: String, offset: Long): RIO[E, List[EventDTO]]

  def persistEvents(events: Seq[EventDTO]): RIO[E, Unit]

  def sendNotifications(competitionId: String, notifications: Seq[CommandProcessorNotification]): RIO[E, Unit]

  def createTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig): RIO[E, Unit]

  def getStateSnapshot(id: String): URIO[E with SnapshotService.Snapshot, Option[CompetitionState]]

  def saveStateSnapshot(state: CompetitionState): URIO[E with SnapshotService.Snapshot, Unit]

  def createInitialState(competitionId: String): LIO[CompetitionState] = RIO {
    CompetitionStateImpl(
      id = competitionId,
      competitors = Option(Map.empty),
      competitionProperties = Option(
        new CompetitionPropertiesDTO().setId(competitionId).setStatus(CompetitionStatus.CREATED)
          .setCreationTimestamp(Instant.now()).setBracketsPublished(false).setSchedulePublished(false)
          .setStaffIds(Array.empty).setEmailNotificationsEnabled(false).setTimeZone("UTC")
      ),
      stages = Some(Map.empty),
      fights = Some(Map.empty),
      categories = Some(Map.empty),
      registrationInfo = Some(
        new RegistrationInfoDTO().setId(competitionId).setRegistrationGroups(Array.empty)
          .setRegistrationPeriods(Array.empty).setRegistrationOpen(false)
      ),
      schedule = Some(new ScheduleDTO().setId(competitionId).setMats(Array.empty).setPeriods(Array.empty)),
      revision = 0L
    )
  }

}

object CommandProcessorOperations {
  case class KafkaTopicConfig(
    numPartitions: Int = 1,
    replicationFactor: Short = 1,
    additionalProperties: Map[String, String] = Map.empty
  )

  private type CommandProcLive = Logging with Clock with Blocking with SnapshotService.Snapshot with Producer[Any, String, Array[Byte]]

  def apply[E](
                adminClient: AdminClient,
                consumerSettings: ConsumerSettings
              ): RIO[E with CommandProcLive, CommandProcessorOperations[E with CommandProcLive]] = {
    for {
      mapper <- ZIO.effect(ObjectMapperFactory.createObjectMapper)
      operations = new CommandProcessorOperations[E with CommandProcLive] {
        override def retrieveEvents(id: String, offset: Long): RIO[E with CommandProcLive, List[EventDTO]] = (for {
          partitions <- Consumer.partitionsFor(id)
          endOffsets <- Consumer
            .endOffsets(partitions.map(pi => new common.TopicPartition(pi.topic(), pi.partition())).toSet)
          res <- Consumer.subscribeAnd(Subscription.topics(id)).plainStream(Serde.string, SerdeApi.eventDeserializer)
            .dropUntil(_.offset.offset >= offset).takeUntil(r => r.offset.offset < endOffsets(r.offset.topicPartition))
            .runCollect.map(_.map(_.value).toList)
        } yield res
          ).provideSomeLayer[Clock with Blocking](Consumer.make(consumerSettings).toLayer)

        override def persistEvents(events: Seq[EventDTO]): RIO[E with CommandProcLive, Unit] = {
          zio.kafka.producer.Producer.produceChunk[Any, String, Array[Byte]](Chunk.fromIterable(events).map(e =>
            new ProducerRecord[String, Array[Byte]](e.getCompetitionId, mapper.writeValueAsBytes(e))
          )).ignore
        }

        override def getStateSnapshot(id: String): URIO[E with CommandProcLive, Option[CompetitionState]] = SnapshotService
          .load(id)

        override def saveStateSnapshot(state: CompetitionState): URIO[E with CommandProcLive, Unit] = SnapshotService
          .save(state)

        override def sendNotifications(
          competitionId: String,
          notifications: Seq[CommandProcessorNotification]
        ): RIO[E with CommandProcLive, Unit] = {
          zio.kafka.producer.Producer.produceChunk[Any, String, Array[Byte]](Chunk.fromIterable(notifications).map(e =>
            new ProducerRecord[String, Array[Byte]](competitionId, mapper.writeValueAsBytes(e))
          )).ignore
        }

        override def createTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig): RIO[E with CommandProcLive, Unit] =
          for {
            topics <- adminClient.listTopics()
            _ <-
              if (topics.contains(topic)) RIO(())
              else adminClient
                .createTopic(AdminClient.NewTopic(topic, topicConfig.numPartitions, topicConfig.replicationFactor))
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
      override def retrieveEvents(
        id: String,
        offset: Long
      ): RIO[Env with Clock with Blocking with Logging, List[EventDTO]] = RIO.effectTotal(List.empty)
      override def persistEvents(events: Seq[EventDTO]): RIO[Env with Clock with Blocking with Logging, Unit] = for {
        _ <- eventReceiver.offerAll(events)
      } yield ()

      override def createInitialState(competitionId: String): LIO[CompetitionState] = {
        initialState.map(RIO.effectTotal(_)).getOrElse(super.createInitialState(competitionId))
      }

      override def getStateSnapshot(
        id: String
      ): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Option[CompetitionState]] = for {
        map <- stateSnapshots.get
      } yield map.get(id)

      override def saveStateSnapshot(
        state: CompetitionState
      ): URIO[Env with Clock with Blocking with Logging with SnapshotService.Snapshot, Unit] = for {
        _ <- stateSnapshots.update(_ + (state.id -> state))
      } yield ()

      override def sendNotifications(
        competitionId: String,
        notifications: Seq[CommandProcessorNotification]
      ): RIO[Env with Clock with Blocking with Logging, Unit] = for {
        _ <- notificationReceiver.offerAll(notifications)
      } yield ()

      override def createTopicIfMissing(
        topic: String,
        topicConfig: KafkaTopicConfig
      ): RIO[Env with Clock with Blocking with Logging, Unit] = RIO(())
    }
  }

}
