package compman.compsrv.logic.actor.kafka

import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.metadata.BrokerState
import org.slf4j.LoggerFactory
import zio.{UIO, URIO, ZIO}
import zio.duration.durationInt

object EmbeddedKafkaBroker extends EmbeddedKafka {
  private val log = LoggerFactory.getLogger(this.getClass)

  val port = 9092

  implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = port, zooKeeperPort = 5555)

  def embeddedKafkaServer: ZIO[Any, Throwable, EmbeddedK] = {
    for {
      server <- startKafkaBroker
      _      <- ZIO.effect(server.broker.awaitShutdown()).fork
      _ <- ZIO.effect {
        while (server.broker.brokerState.get() != BrokerState.RUNNING) {
          ZIO.effect(log.info(s"Starting kafka server.")) *> ZIO.sleep(1.seconds)
        } *> ZIO.sleep(10.seconds)
      }
      _ <- ZIO.effect(log.info(s"Kafka running: localhost:$port"))
    } yield server
  }

  private def startKafkaBroker = { ZIO.effect(EmbeddedKafka.start()) }

  def stopKafkaBroker(server: EmbeddedK): UIO[Unit] = { URIO(EmbeddedKafka.stop(server)) }
}
