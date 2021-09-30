package compman.compsrv.gateway

import cats.effect
import cats.implicits._
import compman.compsrv.gateway.config.AppConfig
import compman.compsrv.gateway.service.GatewayService
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import fs2.concurrent.SignallingRef
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.interop.catz._
import zio.kafka.producer.Producer
import zio.logging.Logging

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path}
import scala.util.Using

object GatewayServiceMain extends zio.App {

  type ServiceIO[A] = RIO[Clock with Blocking with Logging with Producer[Any, String, Array[Byte]], A]

  def server(args: List[String]): ZIO[zio.ZEnv with Clock with Logging, Throwable, Unit] = for {
    config <- AppConfig.load()
    actorSystem <- ActorSystem("queryServiceActorSystem")
    signal <- SignallingRef[ServiceIO, Boolean](false)
    _ <- (for {
      _ <- args.headOption.map(f => if (Files.exists(Path.of(f))) ZIO.unit else signal.set(true)).getOrElse(ZIO.unit)
      _ <- ZIO.sleep(5.seconds)
    } yield ()).forever.fork
    _ <- Logging.debug("Starting server...")
    exitCode <- effect.Ref.of[ServiceIO, effect.ExitCode](effect.ExitCode.Success)
    srv <- ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[ServiceIO].bindHttp(8080, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
        .withHttpApp(
          GatewayService.service().orNotFound
        ).serveWhile(signal, exitCode).compile.drain
    }
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server(args)
    .tapError(t => {
    for {
      errorStr <- ZIO.effect {
        Using.Manager { use =>
          val writer      = use(new StringWriter())
          val printWriter = use(new PrintWriter(writer))
          t.printStackTrace(printWriter)
          writer.toString
        }.getOrElse(t.toString)
      }
      _ <- Logging.error(errorStr)
    } yield ()

  }).fold(_ => ExitCode.failure, _ => ExitCode.success).provideLayer(CompetitionLogging.Live.loggingLayer ++ ZEnv.live)
}
