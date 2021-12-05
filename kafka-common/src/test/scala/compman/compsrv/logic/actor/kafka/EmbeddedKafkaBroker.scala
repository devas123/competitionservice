package compman.compsrv.logic.actor.kafka

import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import zio.{UIO, URIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

object EmbeddedKafkaBroker {
  private val log = LoggerFactory.getLogger(this.getClass)

  val bootstrapServers: AtomicReference[String] = new AtomicReference[String]()

  def embeddedKafkaServer: ZIO[Any, Nothing, KafkaContainer] = {
    for {
      server <- URIO(new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1")))
      _ <- URIO(server.start())
      _ <- URIO(log.info(s"Kafka running: ${server.getFirstMappedPort}"))
      _ <- URIO(bootstrapServers.set(s"localhost:${server.getFirstMappedPort}"))
    } yield server
  }

  def stopKafkaBroker(server: KafkaContainer): UIO[Unit] = {
    URIO(server.stop())
  }
}
