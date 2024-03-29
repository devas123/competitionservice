package compman.compsrv.logic.actor.kafka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.kafka.testkit.scaladsl.TestcontainersKafkaLike
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi.{MessageReceived, QueryFinished, QueryStarted}
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand._
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer,
  StringDeserializer,
  StringSerializer
}

import java.util.{Properties, UUID}
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.DurationInt
import scala.util.Random

class KafkaSupervisorSpec extends SpecBaseWithKafka with TestcontainersKafkaLike {
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
    kafkaSupervisor ! SubscribeToBeginning(notificationTopic, s"group-${UUID.randomUUID()}", messageReceiver.ref)
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
    waitForTopic(notificationTopic)
    (0 until messagesCount).toList
      .foreach(_ => kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification))
    sleep(1.seconds)
    kafkaSupervisor ! SubscribeToEnd(notificationTopic, s"group-${UUID.randomUUID()}", messageReceiver.ref)
    sleep(1.seconds)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, Random.nextBytes(100))
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
    messageReceiver.expectNoMessage(3.seconds)
  }

  test("Should query and subscribe respecting Kafka Committed offset") {
    val topic          = UUID.randomUUID().toString
    val groupId        = s"group-${UUID.randomUUID()}"
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
    waitForTopic(topic)
    kafkaSupervisor ! SubscribeToBeginning(topic, actorId, messageReceiver.ref, commitOffsetToKafka = true)
    sleep(1.seconds)
    (0 until messagesCount).toList.foreach(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))

    (0 until messagesCount).toList.foreach(_ => messageReceiver.expectMessageType[MessageReceived](5.seconds))

    kafkaSupervisor ! Unsubscribe(actorId)
    sleep(1.seconds)
    (0 until messagesCount).toList.foreach(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
    sleep(1.seconds)
    kafkaSupervisor ! Subscribe(topic, groupId, messageReceiver2.ref, Map(0 -> startOffset.toLong))

    (0 until messagesCount - startOffset).toList
      .foreach(_ => messageReceiver2.expectMessageType[MessageReceived](5.seconds))

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
    waitForTopic(notificationTopic)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    sleep(1.seconds)
    kafkaSupervisor ! QueryAsync(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
    messageReceiver.expectMessageType[QueryStarted](5.seconds)
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
    messageReceiver.expectMessageType[MessageReceived](5.seconds)
    messageReceiver.expectMessageType[QueryFinished](5.seconds)
    messageReceiver.expectNoMessage(1.seconds)
    kafkaSupervisor ! SubscribeToEnd(notificationTopic, s"group-${UUID.randomUUID()}", messageReceiver.ref)
    sleep(1.seconds)
    (0 until messagesCount).toList
      .foreach(_ => kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification))
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
    waitForTopic(notificationTopic)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
    val promise = Promise[Seq[Array[Byte]]]()
    sleep(1.seconds)
    kafkaSupervisor ! QuerySync(notificationTopic, UUID.randomUUID().toString, promise, 10.seconds)
    val msgs = Await.result(promise.future, 10.seconds)
    assert(msgs.size == 2)
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
      QuerySync(notificationTopic, UUID.randomUUID().toString, promise, 10.seconds, Map(0 -> startOffset.toLong))
    val msgs = Await.result(promise.future, 10.seconds)
    assert(msgs.isEmpty)
    kafkaSupervisor ! QueryAsync(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
    messageReceiver.expectMessageType[QueryStarted](5.seconds)
    messageReceiver.expectMessageType[QueryFinished](5.seconds)
  }
}
