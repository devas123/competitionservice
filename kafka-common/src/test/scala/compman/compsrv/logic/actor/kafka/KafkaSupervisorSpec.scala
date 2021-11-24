package compman.compsrv.logic.actor.kafka

import compman.compsrv.kafka.EmbeddedKafkaBroker
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, PublishMessage, Subscribe}
import compman.compsrv.logic.actors.{ActorSystem, TestKit}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.ZIO
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.test._
import zio.test.Assertion.isSome
import zio.test.environment.TestEnvironment

import java.util.UUID
import scala.util.Random

object KafkaSupervisorSpec extends DefaultRunnableSpec {
  private val notificationTopic = "notifications"
  private val brokerUrl         = s"localhost:${EmbeddedKafkaBroker.port}"

  override def spec: ZSpec[TestEnvironment, Any] =
    (suite("Kafka supervisor") {
      testM("Should send and receive messages") {
        for {
          _ <- ZIO
            .effect { EmbeddedKafkaBroker.createCustomTopic(notificationTopic, replicationFactor = 1, partitions = 1) }
          actorSystem <- ActorSystem("test")
          kafkaSupervisor <- actorSystem
            .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[Any](List(brokerUrl)))
          messageReceiver <- TestKit[KafkaConsumerApi](actorSystem)
          _ <- kafkaSupervisor ! Subscribe(notificationTopic, UUID.randomUUID().toString, messageReceiver.ref)
          competitionId = "competitionId"
          notification  = Random.nextBytes(100)
          _   <- kafkaSupervisor ! PublishMessage(notificationTopic, competitionId, notification)
          msg <- messageReceiver.expectMessageClass(3.seconds, classOf[Array[Byte]])
        } yield assert(msg)(isSome)
      }
    } @@ TestAspect.around(EmbeddedKafkaBroker.embeddedKafkaServer)(server => EmbeddedKafkaBroker.stopKafkaBroker(server)))
      .provideSomeLayer[TestEnvironment](Clock.live ++ Blocking.live ++ zio.console.Console.live ++ Slf4jLogger.make((_, s) => s, None)).mapError(e => TestFailure.fail(e))

}
