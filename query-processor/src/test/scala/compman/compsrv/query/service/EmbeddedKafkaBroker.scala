package compman.compsrv.query.service

import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import org.slf4j.LoggerFactory
import zio.{UIO, URIO, ZIO}

object EmbeddedKafkaBroker extends EmbeddedKafka {
  private val log = LoggerFactory.getLogger(this.getClass)

  val port: Int = EmbeddedKafkaConfig.defaultKafkaPort

  implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig()

  def embeddedKafkaServer: ZIO[Any, Throwable, EmbeddedK] = {
    for {
      server <- startKafkaBroker
      _      <- ZIO.effect(server.broker.awaitShutdown()).fork
      //      t <- ZIO.effect {
      //        while (server.broker.brokerState.get() != BrokerState.RUNNING) { ZIO.sleep(1.seconds) } *> ZIO.sleep(10.seconds)
      //      }.fork
      //      _ <- t.join.timeout(300.seconds)
      _ <- ZIO.effect(log.info(s"Kafka running: localhost:$port"))
    } yield server
  }

  private def startKafkaBroker = { ZIO.effect(EmbeddedKafka.start()) }

  def stopKafkaBroker(server: EmbeddedK): UIO[Unit] = { URIO(EmbeddedKafka.stop(server)) }
}
