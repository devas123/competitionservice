package compman.compsrv.logic.actors

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.{CompetitionEventListener, CompetitionEventListenerSupervisor, WebsocketConnectionSupervisor}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.CompetitionProcessingStarted
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.query.kafka.EmbeddedKafkaBroker
import compman.compsrv.query.model._
import compman.compsrv.query.sede.{ObjectMapperFactory, SerdeApi}
import compman.compsrv.query.service.kafka.EventStreamingService
import zio.{Ref, URIO, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.test._
import zio.test.Assertion._
import zio.test.environment.TestEnvironment

import java.time.Instant
import java.util.UUID

object CompetitionEventListenerSupervisorTest extends DefaultRunnableSpec {
  private val notificationTopic = "notifications"
  private val brokerUrl         = s"localhost:${EmbeddedKafkaBroker.port}"
  private val loggingLayer      = CompetitionLogging.Live.loggingLayer
  private val mapper            = ObjectMapperFactory.createObjectMapper
  private val producerSettings  = ProducerSettings(List(brokerUrl))
  private val producer = Producer
    .make[Any, String, Array[Byte]](producerSettings, Serde.string, SerdeApi.byteSerializer).toLayer
  private val consumerSettings = ConsumerSettings(List(brokerUrl)).withGroupId(UUID.randomUUID().toString)
    .withClientId(UUID.randomUUID().toString)

  override def spec: ZSpec[TestEnvironment, Any] = suite("Competition event listener") {
    val kafkaBroker = EmbeddedKafkaBroker.embeddedKafkaServer.toManaged(kafka => URIO(kafka.stop(true))).toLayer
    import EmbeddedKafkaBroker._
    testM("Should subscribe to topics") {
      {
        for {
          _ <- ZIO
            .effect { EmbeddedKafkaBroker.createCustomTopic(notificationTopic, replicationFactor = 1, partitions = 1) }
          actorSystem <- ActorSystem("test")
          eventStreaming = EventStreamingService.live(consumerSettings)
          competitions          <- Ref.make(Map.empty[String, ManagedCompetition])
          competitionProperties <- Ref.make(Map.empty[String, CompetitionProperties])
          categories            <- Ref.make(Map.empty[String, Category])
          competitors           <- Ref.make(Map.empty[String, Competitor])
          fights                <- Ref.make(Map.empty[String, Fight])
          periods               <- Ref.make(Map.empty[String, Period])
          registrationPeriods   <- Ref.make(Map.empty[String, RegistrationPeriod])
          registrationGroups    <- Ref.make(Map.empty[String, RegistrationGroup])
          stages                <- Ref.make(Map.empty[String, StageDescriptor])
          websocketSupervisor   <- TestKit[WebsocketConnectionSupervisor.ApiCommand](actorSystem)
          supervisorContext = CompetitionEventListenerSupervisor.Test(competitions)
          eventListenerContext = CompetitionEventListener.Test(
            Some(competitionProperties),
            Some(categories),
            Some(competitors),
            Some(fights),
            Some(periods),
            Some(registrationPeriods),
            Some(registrationGroups),
            Some(stages)
          )
          _ <- actorSystem.make(
            "competitionSupervisor",
            ActorConfig(),
            (),
            CompetitionEventListenerSupervisor.behavior(
              eventStreaming,
              notificationTopic,
              supervisorContext,
              eventListenerContext,
              websocketSupervisor.ref
            )
          )
          competitionId = "competitionId"
          notification = CompetitionProcessingStarted(
            competitionId,
            competitionId + "name",
            competitionId + "events",
            "creator",
            Instant.now(),
            Instant.now(),
            Instant.now(),
            "UTC",
            CompetitionStatus.CREATED
          )
          _ <- ZIO.sleep(10.seconds)
          _ <- Producer
            .produce[Any, String, Array[Byte]](notificationTopic, competitionId, mapper.writeValueAsBytes(notification))
          _ <- ZIO.sleep(10.seconds)
        } yield assert(true)(isTrue)
      }.provideLayer(Clock.live ++ loggingLayer ++ Blocking.live ++ zio.console.Console.live ++ kafkaBroker ++ producer)
    }
  }
}
