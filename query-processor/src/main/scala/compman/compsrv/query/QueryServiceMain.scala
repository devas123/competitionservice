package compman.compsrv.query
import cats.effect
import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.behavior.{CompetitionApiActor, WebsocketConnectionSupervisor}
import compman.compsrv.query.config.AppConfig
import compman.compsrv.query.model.ManagedCompetition
import compman.compsrv.query.service.{CompetitionHttpApiService, WebsocketService}
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import fs2.concurrent.SignallingRef
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import zio.interop.catz._
import zio.logging.Logging

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path}
import scala.util.Using

object QueryServiceMain extends zio.App {

  //  EventStreamingService.live(config.consumer.brokers), config.competitionEventListener.competitionNotificationsTopic
  def server(args: List[String]): ZIO[zio.ZEnv with Clock with Logging, Throwable, Unit] = for {
    (config, cassandraConfig) <- AppConfig.load()
//    cassandraZioSession <- ZIO.effect(
//      CassandraZioSession(cassandraConfig.cluster, cassandraConfig.keyspace, cassandraConfig.preparedStatementCacheSize)
//    )
    actorSystem <- ActorSystem("queryServiceActorSystem")
    webSocketSupervisor <- actorSystem.make(
      "webSocketSupervisor",
      ActorConfig(),
      WebsocketConnectionSupervisor.initialState,
      WebsocketConnectionSupervisor.behavior[ZEnv]
    )
    competitions <- Ref.make(Map.empty[String, ManagedCompetition])
    competitionApiActor <- actorSystem.make(
      "queryApiActor",
      ActorConfig(),
      CompetitionApiActor.initialState,
      CompetitionApiActor.behavior[ZEnv](CompetitionApiActor.Test(competitions))
    )
    signal <- SignallingRef[ServiceIO, Boolean](false)
    _ <- (for {
      _ <- args.headOption.map(f => if (Files.exists(Path.of(f))) ZIO.unit else signal.set(true)).getOrElse(ZIO.unit)
      _ <- ZIO.sleep(5.seconds)
    } yield ()).forever.fork
    _ <- Logging.debug("Starting server...")
    exitCode <- effect.Ref.of[ServiceIO, effect.ExitCode](effect.ExitCode.Success)
    httpApp = Router[ServiceIO](
      "/store" -> CompetitionHttpApiService.service(competitionApiActor),
      "/ws" -> WebsocketService.wsRoutes(webSocketSupervisor)
    ).orNotFound
    srv <- ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[ServiceIO].bindHttp(8080, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
        .withHttpApp(
          httpApp
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
