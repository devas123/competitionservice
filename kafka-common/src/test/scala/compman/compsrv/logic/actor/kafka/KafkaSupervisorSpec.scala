package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import zio.{Has, Promise, URIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{CommittableRecord, Consumer, Subscription}
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.stream.ZStream
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.UUID
import scala.util.{Random, Try}

object KafkaSupervisorSpec extends DefaultRunnableSpec {
  val loggingLayer: ZLayer[Any, Throwable, Logging]                       = CompetitionLogging.Live.loggingLayer
  val layers: ZLayer[Any, Throwable, Clock with Blocking]                 = Clock.live ++ Blocking.live
  val allLayers: ZLayer[Any, Throwable, Clock with Blocking with Logging] = Clock.live ++ Blocking.live ++ loggingLayer

  import cats.implicits._
  import zio.interop.catz._

  override def spec: ZSpec[Any, Throwable] =
    suite("Kafka supervisor")(
      testM("Should send and receive messages") {
        ActorSystem("Test").use { actorSystem =>
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            notificationTopic = UUID.randomUUID().toString
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
            _ <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
            _ <- kafkaSupervisor ! Subscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
            competitionId = "competitionId"
            notification = Random.nextBytes(100)
            _ <- kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
            msg <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
          } yield assert(msg)(isSome)
        }.provideLayer(allLayers)
      },
      testM("Should query and subscribe") {
        ActorSystem("Test").use { actorSystem =>
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            messagesCount = 10
            topic = UUID.randomUUID().toString
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            competitionId = "competitionId"
            notification = Random.nextBytes(100)
            messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
            _ <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
            _ <- ZIO.sleep(2.seconds)
            _ <- kafkaSupervisor ! PublishMessage(topic, competitionId, notification)
            _ <- kafkaSupervisor ! PublishMessage(topic, competitionId, notification)
            _ <- ZIO.sleep(1.seconds)
            _ <- kafkaSupervisor ! QueryAndSubscribe(topic, UUID.randomUUID().toString, messageReceiver.ref)
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryStarted])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryFinished])
            _ <- (0 until messagesCount - 2).toList
              .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
            _ <- ZIO.sleep(1.seconds)
            msgs <- (0 until messagesCount).toList
              .traverse(_ => messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined))
          } yield assert(msgs.filter(a => a))(hasSize(equalTo(messagesCount)))
        }.provideLayer(allLayers)
      },
      testM("Should execute query async.") {
        ActorSystem("Test").use { actorSystem =>
          val messagesCount = 10
          val topic = UUID.randomUUID().toString
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
            _ <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
            competitionId = "competitionId"
            notification = Random.nextBytes(100)
            _ <- (0 until messagesCount).toList
              .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
            _ <- ZIO.sleep(1.seconds)
            _ <- kafkaSupervisor ! QueryAsync(topic, UUID.randomUUID().toString, messageReceiver.ref)
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryStarted])
            msgs <- (0 until messagesCount).toList
              .traverse(_ => messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined))
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryFinished])
            filtered = msgs.filter(p => p)
          } yield assert(filtered)(hasSize(equalTo(messagesCount)))
        }.provideLayer(allLayers)
      },
      testM("Should execute query Sync.") {
        ActorSystem("Test").use { actorSystem =>
          val messagesCount = 10
          val topic = UUID.randomUUID().toString
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            _ <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
            competitionId = "competitionId"
            notification = Random.nextBytes(100)
            _ <- (0 until messagesCount).toList
              .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
            _ <- ZIO.sleep(1.seconds)
            promise <- Promise.make[Throwable, Seq[Array[Byte]]]
            _ <- kafkaSupervisor ! QuerySync(topic, UUID.randomUUID().toString, promise, 10.seconds)
            msgs <- promise.await
          } yield assert(msgs)(hasSize(equalTo(messagesCount)))
        }.provideLayer(allLayers)
      },
      testM("Should execute query Sync and async on empty topic") {
        ActorSystem("Test").use { actorSystem =>
          val messagesCount = 0
          val topic = UUID.randomUUID().toString
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            _ <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
            _ <- ZIO.sleep(1.seconds)
            promise <- Promise.make[Throwable, Seq[Array[Byte]]]
            _ <- kafkaSupervisor ! QuerySync(topic, UUID.randomUUID().toString, promise, 10.seconds)
            msgs <- promise.await
            _ <- kafkaSupervisor ! QueryAsync(topic, UUID.randomUUID().toString, messageReceiver.ref)
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryStarted])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryFinished])
          } yield assert(msgs)(hasSize(equalTo(messagesCount)))
        }.provideLayer(allLayers)
      }
    ) @@ sequential @@ aroundAll(EmbeddedKafkaBroker.embeddedKafkaServer)(server => URIO(server.stop()))

  def getByteArrayStream(notificationTopic: String): ZStream[Any with Clock with Blocking with Has[
    Consumer
  ], Throwable, CommittableRecord[String, Try[Array[Byte]]]] = {
    Consumer.subscribeAnd(Subscription.topics(notificationTopic)).plainStream(Serde.string, Serde.byteArray.asTry)
  }

}
