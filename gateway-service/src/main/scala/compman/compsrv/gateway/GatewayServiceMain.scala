package compman.compsrv.gateway

import cats.effect
import compman.compsrv.gateway.config.AppConfig
import compman.compsrv.gateway.service.GatewayService
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import fs2.concurrent.SignallingRef
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.interop.catz._
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.logging.Logging

import java.nio.file.{Files, Path}

object GatewayServiceMain extends zio.App {

  type ServiceEnv   = Clock with Blocking with Logging with Has[Producer]
  type ServiceIO[A] = RIO[ServiceEnv, A]

  def server(args: List[String]): ZIO[zio.ZEnv with ServiceEnv, Throwable, Unit] = for {
    signal <- SignallingRef[ServiceIO, Boolean](false)
    _ <- (for {
      _ <- args.headOption.map(f => signal.set(true).unless(Files.exists(Path.of(f)))).getOrElse(ZIO.unit)
      _ <- ZIO.sleep(5.seconds)
    } yield ()).forever.fork
    _        <- Logging.debug("Starting server...")
    exitCode <- effect.Ref.of[ServiceIO, effect.ExitCode](effect.ExitCode.Success)
    srv <- ZIO.runtime[ZEnv] *> {
      BlazeServerBuilder[ServiceIO].bindHttp(8080, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
        .withHttpApp(GatewayService.service().orNotFound).serveWhile(signal, exitCode).compile.drain
    }
  } yield srv

  def createServer(args: List[String]): ZIO[zio.ZEnv with Logging, Throwable, Unit] = for {
    config <- AppConfig.load()
    producerSettings = ProducerSettings(config.producer.brokers)
    producerLayer    = Producer.make(producerSettings).toLayer
    srv <- server(args).provideSomeLayer[ZEnv with Logging](producerLayer)
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {srv <- createServer(args)} yield srv).tapError(logError).exitCode
      .provideLayer(CompetitionLogging.Live.loggingLayer ++ ZEnv.live)
  }

}
