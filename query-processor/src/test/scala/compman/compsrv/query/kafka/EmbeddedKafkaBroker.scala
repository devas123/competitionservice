package compman.compsrv.query.kafka

import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.slf4j.LoggerFactory
import zio.{Task, ZIO}

object EmbeddedKafkaBroker extends EmbeddedKafka {
  private val log = LoggerFactory.getLogger(this.getClass)

  val port = 9092

  implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = port, zooKeeperPort = 5555)

  def embeddedKafkaServer: Task[EmbeddedK] = {
    for {
      server <- ZIO.effect(EmbeddedKafka.start())
      _ <- ZIO.effect(server.broker.awaitShutdown()).fork
      _ <- ZIO.effect(log.info(s"Kafka running: localhost: $port"))
    } yield server
  }
}
