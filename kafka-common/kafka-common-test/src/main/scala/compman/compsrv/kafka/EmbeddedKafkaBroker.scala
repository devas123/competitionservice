package compman.compsrv.kafka

import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.metadata.BrokerState
import org.slf4j.LoggerFactory
import zio.{RIO, Task, URIO, ZIO}
import zio.clock.Clock
import zio.duration.durationInt

object EmbeddedKafkaBroker extends EmbeddedKafka {
  private val log = LoggerFactory.getLogger(this.getClass)

  val port = 9092

  implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = port, zooKeeperPort = 5555)

  def embeddedKafkaServer: RIO[Clock, EmbeddedK] = {
    for {
      server <- startKafkaBroker
      _      <- ZIO.effect(server.broker.awaitShutdown()).fork
      t <- ZIO.effect {
        while (server.broker.brokerState.get() != BrokerState.RUNNING) { ZIO.sleep(1.seconds) } *> ZIO.sleep(10.seconds)
      }.fork
      _ <- t.join.timeout(300.seconds)
      _ <- ZIO.effect(log.info(s"Kafka running: localhost: $port"))
    } yield server
  }

  private def startKafkaBroker: Task[EmbeddedK] = { ZIO.effect(EmbeddedKafka.start()) }

  def stopKafkaBroker(server: EmbeddedK): URIO[Any, Any] = { URIO(EmbeddedKafka.stop(server)) }
}
