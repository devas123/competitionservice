package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import zio.{Has, URIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.stream.ZStream
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.aroundAll

import java.util.UUID
import scala.util.{Random, Try}

object KafkaSupervisorSpec extends DefaultRunnableSpec {
  private val notificationTopic                     = "notifications"
  private val brokerUrl                             = s"localhost:${EmbeddedKafkaBroker.port}"
  val loggingLayer: ZLayer[Any, Throwable, Logging] = CompetitionLogging.Live.loggingLayer

  val consumerLayer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]] = Consumer.make(
    ConsumerSettings(List(brokerUrl)).withGroupId(UUID.randomUUID().toString)
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(Consumer.AutoOffsetStrategy.Earliest))
  ).toLayer
  val layers: ZLayer[Any, Throwable, Clock with Blocking] = Clock.live ++ Blocking.live

  val allLayers: ZLayer[Any, Throwable, Clock with Blocking with Logging] =
    Clock.live ++ Blocking.live ++ loggingLayer

  override def spec: ZSpec[Any, Throwable] = suite("Kafka supervisor") {
    testM("Should send and receive messages") {
      (for {
        actorSystem <- ActorSystem("test")
        kafkaSupervisor <- actorSystem
          .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
        messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
        _               <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
        _ <- kafkaSupervisor ! Subscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
        competitionId = "competitionId"
        notification  = Random.nextBytes(100)
        _   <- kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
        msg <- messageReceiver.expectMessageClass(15.seconds, classOf[MessageReceived])
      } yield assert(msg)(isSome)).provideLayer(allLayers)
    }
  } @@ aroundAll(EmbeddedKafkaBroker.embeddedKafkaServer)(server => URIO(server.stop(true)))

  def getByteArrayStream(
    notificationTopic: String
  ): ZStream[Any with Clock with Blocking with Consumer, Throwable, CommittableRecord[String, Try[Array[Byte]]]] = {
    Consumer.subscribeAnd(Subscription.topics(notificationTopic)).plainStream(Serde.string, Serde.byteArray.asTry)
  }

}
