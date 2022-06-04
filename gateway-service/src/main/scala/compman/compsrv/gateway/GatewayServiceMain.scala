package compman.compsrv.gateway

import compman.compsrv.gateway.actors.CommandForwardingActor
import compman.compsrv.gateway.config.AppConfig
import compman.compsrv.gateway.service.GatewayService
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.logic.actors.ActorSystem
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.logging.Logging

object GatewayServiceMain extends zio.App {

  type ServiceEnv   = Clock with Logging with Blocking
  type ServiceIO[A] = RIO[ServiceEnv, A]

  def server(config: AppConfig): ZIO[zio.ZEnv with Clock with Logging, Throwable, Unit] = ActorSystem("gateway-service")
    .use { actorSystem =>
      for {
        kafkaSupervisor <- actorSystem
          .make("kafkaSupervisor", ActorConfig(), KafkaSupervisor.initialState, KafkaSupervisor.behavior[ZEnv](config.producer.brokers))
        _ <- kafkaSupervisor ! CreateTopicIfMissing(config.consumer.callbackTopic, KafkaTopicConfig())
        commandForwardingActor <- actorSystem.make(
          "commandForwarder",
          ActorConfig(),
          CommandForwardingActor.initialState,
          CommandForwardingActor.behavior[ZEnv](
            kafkaSupervisorActor = kafkaSupervisor,
            producerConfig = config.producer,
            consumerConfig = config.consumer,
            groupId = config.consumer.groupId,
            config.callbackTimeoutMs
          )
        )
        _ <- Logging.debug("Starting server...")
        srv <- ZIO.runtime[ZEnv] *> {
          BlazeServerBuilder[ServiceIO].bindHttp(8080, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
            .withHttpApp(GatewayService.service(commandForwardingActor).orNotFound).serve.compile.drain
        }
      } yield srv
    }

  def createServer(config: AppConfig): ZIO[zio.ZEnv with ServiceEnv, Throwable, Unit] = for {
    producerSettings <- ZIO.effect(ProducerSettings(config.producer.brokers))
    producerLayer = Producer.make(producerSettings).toLayer
    srv <- server(config).provideSomeLayer[ZEnv with Logging](producerLayer)
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {
      config <- AppConfig.load()
      srv    <- createServer(config)
    } yield srv).tapError(logError).exitCode.provideLayer(CompetitionLogging.Live.loggingLayer ++ ZEnv.live)
  }

}
