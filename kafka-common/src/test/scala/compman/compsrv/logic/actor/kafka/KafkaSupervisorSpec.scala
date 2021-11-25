package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import zio.{URIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.Logging
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.UUID
import scala.util.Random

object KafkaSupervisorSpec extends DefaultRunnableSpec {
  private val notificationTopic                     = "notifications"
  private val brokerUrl                             = s"localhost:${EmbeddedKafkaBroker.port}"
  val loggingLayer: ZLayer[Any, Throwable, Logging] = CompetitionLogging.Live.loggingLayer
  val layers: ZLayer[Any, Throwable, Clock with Blocking with Logging] = Clock.live ++ Blocking.live ++ loggingLayer

  override def spec: ZSpec[Any, Throwable] = suite("Kafka supervisor") {
    testM("Should send and receive messages") {
      (for {
        actorSystem <- ActorSystem("test")
        kafkaSupervisor <- actorSystem
          .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
        messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
        _ <- kafkaSupervisor ! CreateTopicIfMissing(notificationTopic, KafkaTopicConfig())
        _ <- kafkaSupervisor ! Subscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
        competitionId = "competitionId"
        notification  = Random.nextBytes(100)
        _   <- kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
        msg <- messageReceiver.expectMessageClass(3.seconds, classOf[Array[Byte]])
      } yield assert(msg)(isSome)).provideLayer(layers)
    }
  } @@ aroundAll(EmbeddedKafkaBroker.embeddedKafkaServer)(server => URIO(server.stop(true)))
}
