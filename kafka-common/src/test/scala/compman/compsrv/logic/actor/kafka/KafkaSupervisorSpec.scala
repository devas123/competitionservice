package compman.compsrv.logic.actor.kafka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.kafka.testkit.scaladsl.TestcontainersKafkaLike
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}

import java.util.{Properties, UUID}
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.DurationInt
import scala.util.Random

class KafkaSupervisorSpec extends SpecBase with TestcontainersKafkaLike {
  val messagesCount = 10
  val startOffset   = 5

  val mockedMessageReceiverBehavior: Behavior[KafkaConsumerApi] = Behaviors.receiveMessage[KafkaConsumerApi] { _ =>
    Behaviors.same
  }

  test("Should send and receive messages") {
    val notificationTopic = UUID.randomUUID().toString
    val producerConfig    = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val messageReceiver = actorTestKit.createTestProbe[KafkaConsumerApi]()
    val properties      = new Properties()

    properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    actorTestKit.spawn(Behaviors.monitor(messageReceiver.ref, mockedMessageReceiverBehavior))
    kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
    waitForTopic(notificationTopic)
    kafkaSupervisor !
      QueryAndSubscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref, startOffset = None)
    val competitionId = "competitionId"
    val notification  = Random.nextBytes(100)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
  }

  test("Should reset to latest offset by default") {
    val notificationTopic = UUID.randomUUID().toString
    val producerConfig    = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val messageReceiver = actorTestKit.createTestProbe[KafkaConsumerApi]()
    val notification    = Random.nextBytes(100)
    val competitionId   = "competitionId"
    actorTestKit.spawn(Behaviors.monitor(messageReceiver.ref, mockedMessageReceiverBehavior))
    kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
    (0 until messagesCount).toList
      .foreach(_ => kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification))
    kafkaSupervisor ! QueryAndSubscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, Random.nextBytes(100))
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
    messageReceiver.expectNoMessage(5.seconds)
  }

  test("Should query and subscribe respecting Kafka Committed offset") {
    val topic          = UUID.randomUUID().toString
    val groupId        = UUID.randomUUID().toString
    val actorId        = UUID.randomUUID().toString
    val producerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val messageReceiver  = actorTestKit.createTestProbe[KafkaConsumerApi]()
    val messageReceiver2 = actorTestKit.createTestProbe[KafkaConsumerApi]()
    val notification     = Random.nextBytes(100)
    val competitionId    = "competitionId"
    actorTestKit.spawn(Behaviors.monitor(messageReceiver.ref, mockedMessageReceiverBehavior))
    actorTestKit.spawn(Behaviors.monitor(messageReceiver2.ref, mockedMessageReceiverBehavior))
    kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
    kafkaSupervisor ! QueryAndSubscribe(topic, actorId, messageReceiver.ref)

    (0 until messagesCount).toList.foreach(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))

    (0 until messagesCount).toList.foreach(_ => messageReceiver.expectMessageType[MessageReceived](5.seconds))

    kafkaSupervisor ! Unsubscribe(actorId)

    (0 until messagesCount).toList.foreach(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))

    kafkaSupervisor !
      QueryAndSubscribe(topic, groupId, messageReceiver2.ref, Some(startOffset.toLong), Some(messagesCount.toLong))

    messageReceiver2.expectMessageType[QueryStarted](5.seconds)
    (0 until messagesCount - startOffset).toList
      .foreach(_ => messageReceiver2.expectMessageType[MessageReceived](5.seconds))
    messageReceiver2.expectMessageType[QueryFinished](5.seconds)

    (0 until messagesCount).toList.foreach(_ => messageReceiver2.expectMessageType[MessageReceived](5.seconds))

  }

  test("Should query and subscribe") {
    val notificationTopic = UUID.randomUUID().toString
    val producerConfig    = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val messageReceiver = actorTestKit.createTestProbe[KafkaConsumerApi]()
    val notification    = Random.nextBytes(100)
    val competitionId   = "competitionId"
    actorTestKit.spawn(Behaviors.monitor(messageReceiver.ref, mockedMessageReceiverBehavior))
    kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    kafkaSupervisor ! QueryAsync(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
    messageReceiver.expectMessageType[QueryStarted](5.seconds)
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
    messageReceiver.expectMessageType[QueryFinished](5.seconds)
    messageReceiver.expectNoMessage(5.seconds)
    kafkaSupervisor ! QueryAndSubscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
    (0 until messagesCount).toList.foreach(_ => messageReceiver.expectMessageType[MessageReceived](5.seconds))
    messageReceiver.expectNoMessage()
  }

  test("Should execute query Sync.") {
    val notificationTopic = UUID.randomUUID().toString
    val producerConfig    = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val messageReceiver = actorTestKit.createTestProbe[KafkaConsumerApi]()
    val notification    = Random.nextBytes(100)
    val competitionId   = "competitionId"
    actorTestKit.spawn(Behaviors.monitor(messageReceiver.ref, mockedMessageReceiverBehavior))
    kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    val promise = Promise[Seq[Array[Byte]]]()
    kafkaSupervisor !
      QuerySync(notificationTopic, UUID.randomUUID().toString, promise, 10.seconds, Some(startOffset.toLong))
    val msgs = Await.result(promise.future, 10.seconds)
    assert(msgs.size == messagesCount - startOffset)
  }

  test("Should execute query Sync and async on empty topic") {
    val notificationTopic = UUID.randomUUID().toString
    val producerConfig    = actorTestKit.system.settings.config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = actorTestKit.system.settings.config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    val kafkaSupervisor = actorTestKit
      .spawn(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
    val messageReceiver = actorTestKit.createTestProbe[KafkaConsumerApi]()
    actorTestKit.spawn(Behaviors.monitor(messageReceiver.ref, mockedMessageReceiverBehavior))
    kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
    val promise = Promise[Seq[Array[Byte]]]()
    kafkaSupervisor !
      QuerySync(notificationTopic, UUID.randomUUID().toString, promise, 10.seconds, Some(startOffset.toLong))
    val msgs = Await.result(promise.future, 10.seconds)
    assert(msgs.nonEmpty)
    kafkaSupervisor ! QueryAsync(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
    messageReceiver.expectMessageType[QueryStarted](5.seconds)
    messageReceiver.expectMessageType[QueryFinished](5.seconds)
  }
}
