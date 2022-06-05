package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import zio.{random, Has, Promise, URIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
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
  val loggingLayer: ZLayer[Any, Throwable, Logging]       = CompetitionLogging.Live.loggingLayer
  val layers: ZLayer[Any, Throwable, Clock with Blocking] = Clock.live ++ Blocking.live
  val allLayers: ZLayer[Any, Throwable, Clock with Blocking with Logging with Console] = Clock.live ++ Blocking.live ++
    loggingLayer ++ zio.console.Console.live ++ zio.random.Random.live ++ TestConfig.live(samples0 = 5, repeats0 = 1, retries0 = 1, shrinks0 = 0)
  val messagesCount = 10
  val startOffset = 5
  val startOffsetGen: Gen[random.Random, Int] = Gen.bounded(0, messagesCount - 1)(_ => Gen.anyInt)

  import cats.implicits._
  import zio.interop.catz._

  override def spec: ZSpec[Any with zio.random.Random with TestConfig, Throwable] = suite("Kafka supervisor")(
    testM("Should send and receive messages") {
      ActorSystem("Test").use { actorSystem =>
        for {
          brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
          notificationTopic = UUID.randomUUID().toString
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
          _               <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
          _ <- kafkaSupervisor ! Subscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
          _ <- Logging.info("Subscribed.")
          _ <- ZIO.sleep(10.seconds)
          competitionId = "competitionId"
          notification  = Random.nextBytes(100)
          _ <- Logging.info("Publishing message...")
          _   <- kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
          msg <- messageReceiver.expectMessageClass(5.seconds, classOf[MessageReceived])
        } yield assert(msg)(isSome)
      }.provideLayer(allLayers)
    },
    testM("Should query and subscribe respecting Kafka Committed offset") {
      ActorSystem("Test").use { actorSystem =>
        for {
          brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
          topic = UUID.randomUUID().toString
          groupId = UUID.randomUUID().toString
          actorId = UUID.randomUUID().toString
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          competitionId = "competitionId"
          notification  = Random.nextBytes(100)
          messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
          messageReceiver2 <- TestKit[KafkaConsumerApi](actorSystem)
          _               <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
          _ <- kafkaSupervisor ! Subscribe(topic, groupId, messageReceiver.ref, actorId)
          _ <- ZIO.sleep(5.seconds)
          _ <- (0 until messagesCount).toList
            .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
          _ <- ZIO.sleep(1.seconds)
          msgs <- (0 until messagesCount).toList
            .traverse(i => messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined).onExit(_ => Logging.info(s"Received a message first time ${i + 1}")))
          _ <- ZIO.sleep(4.seconds)
          _ <- kafkaSupervisor ! Unsubscribe(actorId)
          _ <- ZIO.sleep(4.seconds)
          _ <- (0 until messagesCount).toList
            .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
          _ <- ZIO.sleep(3.seconds)
          _ <- kafkaSupervisor ! QueryAndSubscribe(topic, groupId, messageReceiver2.ref, startOffset.toLong)
          _ <- messageReceiver2.expectMessageClass(15.seconds, classOf[QueryStarted])
          _ <- (0 until messagesCount - startOffset).toList
            .traverse(_ => messageReceiver2.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined))
          _ <- messageReceiver2.expectMessageClass(15.seconds, classOf[QueryFinished])
          _ <- Logging.info("Query finished. Waiting for the other messages now.")
          msgs2 <- (0 until messagesCount).toList
            .traverse(i => messageReceiver2.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined).onExit(_ => Logging.info(s"Received a message ${i + 1}")))
        } yield assert(msgs.filter(a => a))(hasSize(equalTo(messagesCount))) && assert(msgs2.filter(a => a))(hasSize(equalTo(messagesCount)))
      }.provideLayer(allLayers)
    },
    testM("Should query and subscribe") {
        ActorSystem("Test").use { actorSystem =>
          for {
            brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
            topic = UUID.randomUUID().toString
            kafkaSupervisor <- actorSystem
              .make("kafkaSupervisor", ActorConfig(), initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
            competitionId = "competitionId"
            notification  = Random.nextBytes(100)
            messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
            _               <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
            _               <- ZIO.sleep(2.seconds)
            _               <- kafkaSupervisor ! PublishMessage(topic, competitionId, notification)
            _               <- kafkaSupervisor ! PublishMessage(topic, competitionId, notification)
            _               <- ZIO.sleep(1.seconds)
            _ <- kafkaSupervisor ! QueryAsync(topic, UUID.randomUUID().toString, messageReceiver.ref)
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryStarted])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
            _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryFinished])
            _ <- kafkaSupervisor ! Subscribe(topic, UUID.randomUUID().toString, messageReceiver.ref)
            _ <- ZIO.sleep(2.seconds)
            _ <- (0 until messagesCount).toList
              .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
            _ <- ZIO.sleep(1.seconds)
            msgs <- (0 until messagesCount).toList
              .traverse(_ => messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined))
          } yield assert(msgs.filter(a => a))(hasSize(equalTo(messagesCount)))
        }.provideLayer(allLayers)
    },
    testM("Should execute query async.") {
      ActorSystem("Test").use { actorSystem =>
        val topic         = UUID.randomUUID().toString
        for {
          brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
          _               <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
          competitionId = "competitionId"
          notification  = Random.nextBytes(100)
          _ <- (0 until messagesCount).toList
            .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
          _ <- ZIO.sleep(5.seconds)
          _ <- kafkaSupervisor ! QueryAsync(topic, UUID.randomUUID().toString, messageReceiver.ref, startOffset.toLong)
          _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryStarted])
          msgs <- (0 until messagesCount - startOffset).toList
            .traverse(_ => messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived]).map(_.isDefined))
          _ <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryFinished])
          filtered = msgs.filter(p => p)
        } yield assert(filtered)(hasSize(equalTo(messagesCount - startOffset)))
      }.provideLayer(allLayers)
    },
    testM("Should execute query Sync.") {
      ActorSystem("Test").use { actorSystem =>
        val topic         = UUID.randomUUID().toString
        for {
          brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          _ <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
          competitionId = "competitionId"
          notification  = Random.nextBytes(100)
          _ <- (0 until messagesCount).toList
            .traverse(_ => kafkaSupervisor ! PublishMessage(topic, competitionId, notification))
          _       <- ZIO.sleep(5.seconds)
          promise <- Promise.make[Throwable, Seq[Array[Byte]]]
          _       <- kafkaSupervisor ! QuerySync(topic, UUID.randomUUID().toString, promise, 10.seconds, startOffset.toLong)
          msgs    <- promise.await
        } yield assert(msgs)(hasSize(equalTo(messagesCount - startOffset)))
      }.provideLayer(allLayers)
    },
    testM("Should execute query Sync and async on empty topic") {
      ActorSystem("Test").use { actorSystem =>
        val messagesCount = 0
        val topic         = UUID.randomUUID().toString
        for {
          brokerUrl       <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
          messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), initialState, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          _       <- kafkaSupervisor ! CreateTopicIfMissing(topic, KafkaTopicConfig())
          _       <- ZIO.sleep(1.seconds)
          promise <- Promise.make[Throwable, Seq[Array[Byte]]]
          _       <- kafkaSupervisor ! QuerySync(topic, UUID.randomUUID().toString, promise, 10.seconds)
          msgs    <- promise.await
          _       <- kafkaSupervisor ! QueryAsync(topic, UUID.randomUUID().toString, messageReceiver.ref)
          _       <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryStarted])
          _       <- messageReceiver.expectMessageClass(15.seconds, classOf[QueryFinished])
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
