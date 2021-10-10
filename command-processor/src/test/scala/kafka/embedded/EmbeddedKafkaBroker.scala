package kafka.embedded

import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.slf4j.LoggerFactory

object EmbeddedKafkaBroker extends App with EmbeddedKafka {
  val log = LoggerFactory.getLogger(this.getClass)

  val port = 9092

  implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = port, zooKeeperPort = 5555)

  val embeddedKafkaServer: EmbeddedK = EmbeddedKafka.start()

   log.info(s"Kafka running: localhost: $port")

  embeddedKafkaServer.broker.awaitShutdown()
}
