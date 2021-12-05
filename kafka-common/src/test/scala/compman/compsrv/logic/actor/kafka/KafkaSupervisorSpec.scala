package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.logging.CompetitionLogging
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{CommittableRecord, Consumer, Subscription}
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.aroundAll
import zio.test._
import zio.{Has, URIO, ZIO, ZLayer}

import java.util.UUID
import scala.util.{Random, Try}

object KafkaSupervisorSpec extends DefaultRunnableSpec {
  private val notificationTopic = "notifications"
  val loggingLayer: ZLayer[Any, Throwable, Logging] = CompetitionLogging.Live.loggingLayer
  val layers: ZLayer[Any, Throwable, Clock with Blocking] = Clock.live ++ Blocking.live
  val allLayers: ZLayer[Any, Throwable, Clock with Blocking with Logging] =
    Clock.live ++ Blocking.live ++ loggingLayer

  override def spec: ZSpec[Any, Throwable] = suite("Kafka supervisor") {
    testM("Should send and receive messages") {
      (for {
        actorSystem <- ActorSystem("test")
        brokerUrl <- ZIO.effectTotal(EmbeddedKafkaBroker.bootstrapServers.get())
        kafkaSupervisor <- actorSystem
          .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
        messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
        _ <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
        _ <- kafkaSupervisor ! Subscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
        competitionId = "competitionId"
        notification = Random.nextBytes(100)
        _ <- kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
        msg <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
      } yield assert(msg)(isSome)).provideLayer(allLayers)
    }
  } @@ aroundAll(EmbeddedKafkaBroker.embeddedKafkaServer)(server => URIO(server.stop()))

  def getByteArrayStream(
    notificationTopic: String
  ): ZStream[Any with Clock with Blocking with Has[Consumer], Throwable, CommittableRecord[String, Try[Array[Byte]]]] = {
    Consumer.subscribeAnd(Subscription.topics(notificationTopic)).plainStream(Serde.string, Serde.byteArray.asTry)
  }

}
