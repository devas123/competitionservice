package compman.compsrv.logic.actors

import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.kafka.testkit.scaladsl.TestcontainersKafkaLike
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.query.model._
import compman.compsrv.SpecBaseWithKafka
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.query.actors.behavior.{CompetitionEventListener, CompetitionEventListenerSupervisor, WebsocketConnectionSupervisor}
import compservice.model.protobuf.model.{
  CompetitionProcessingStarted,
  CompetitionProcessorNotification,
  CompetitionStatus
}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer,
  StringDeserializer,
  StringSerializer
}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DurationInt

class CompetitionEventListenerSupervisorSpec extends SpecBaseWithKafka with TestcontainersKafkaLike {
  private val notificationTopic = "notifications"
  private val callbackTopic     = "callback"

  test("Should subscribe to topics") {
    val competitions          = new AtomicReference(Map.empty[String, ManagedCompetition])
    val competitionProperties = new AtomicReference(Map.empty[String, CompetitionProperties])
    val categories            = new AtomicReference(Map.empty[String, Category])
    val competitors           = new AtomicReference(Map.empty[String, Competitor])
    val fights                = new AtomicReference(Map.empty[String, Fight])
    val periods               = new AtomicReference(Map.empty[String, Period])
    val registrationInfo      = new AtomicReference(Map.empty[String, RegistrationInfo])
    val stages                = new AtomicReference(Map.empty[String, StageDescriptor])
    val producerConfig        = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val websocketSupervisor = actorTestKit.createTestProbe[WebsocketConnectionSupervisor.ApiCommand]()

    kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
    kafkaSupervisor ! CreateTopicIfMissing(callbackTopic, KafkaTopicConfig())
    waitForTopic(notificationTopic)
    waitForTopic(callbackTopic)
    val supervisorContext = CompetitionEventListenerSupervisor.Test(competitions)
    val eventListenerContext = CompetitionEventListener.Test(
      Some(competitionProperties),
      Some(categories),
      Some(competitors),
      Some(fights),
      Some(periods),
      Some(registrationInfo),
      Some(stages)
    )
    actorTestKit.spawn(
      CompetitionEventListenerSupervisor.behavior(
        notificationTopic,
        callbackTopic,
        supervisorContext,
        kafkaSupervisor,
        eventListenerContext,
        websocketSupervisor.ref
      ),
      "competitionSupervisor"
    )
    val competitionId = "competitionId"
    val notification = CompetitionProcessorNotification().withStarted(CompetitionProcessingStarted(
      competitionId,
      competitionId + "name",
      competitionId + "events",
      "creator",
      Some(Instant.now().asTimestamp),
      Some(Instant.now().asTimestamp),
      Some(Instant.now().asTimestamp),
      "UTC",
      CompetitionStatus.CREATED
    ))
    sleep(3.seconds)
    val producer = producerSettings.createKafkaProducer()
    producer.send(new ProducerRecord[String, Array[Byte]](notificationTopic, competitionId, notification.toByteArray))
  }
}
