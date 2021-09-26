package compman.compsrv.query
import cats.implicits._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.query.actors.ActorSystem
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.actors.behavior.{CompetitionApiActor, WebsocketConnectionSupervisor}
import compman.compsrv.query.config.AppConfig
import compman.compsrv.query.service.{CompetitionHttpApiService, WebsocketService}
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import io.getquill.CassandraZioSession
import org.http4s.blaze.server.BlazeServerBuilder
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.Logging

import java.io.{PrintWriter, StringWriter}

object QueryServiceMain extends zio.App {

  //  EventStreamingService.live(config.consumer.brokers), config.competitionEventListener.competitionNotificationsTopic
  val server: ZIO[zio.ZEnv with Clock with Logging, Throwable, Unit] = for {
    (config, cassandraConfig) <- AppConfig.load()
    cassandraZioSession <- ZIO.effect(
      CassandraZioSession(cassandraConfig.cluster, cassandraConfig.keyspace, cassandraConfig.preparedStatementCacheSize)
    )
    actorSystem <- ActorSystem("queryServiceActorSystem")
    webSocketSupervisor <- actorSystem.make(
      "webSocketSupervisor",
      ActorConfig(),
      WebsocketConnectionSupervisor.initialState,
      WebsocketConnectionSupervisor.behavior[ZEnv]
    )
    competitionApiActor <- actorSystem.make(
      "queryApiActor",
      ActorConfig(),
      CompetitionApiActor.initialState,
      CompetitionApiActor.behavior[ZEnv](CompetitionApiActor.Live(cassandraZioSession))
    )
    srv <- ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[ServiceIO].bindHttp(8080, "0.0.0.0").withHttpApp(
        CompetitionHttpApiService.service(competitionApiActor) <+> WebsocketService.wsRoutes(webSocketSupervisor)
      ).withWebSockets(true).serve.compile.drain
    }
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server.tapError(t => {
    for {
      errorStr <- ZIO.effect {
        val writer = new StringWriter()
        t.printStackTrace(new PrintWriter(writer))
        writer.toString
      }
      _ <- Logging.error(errorStr)
    } yield ()

  }).fold(_ => ExitCode.failure, _ => ExitCode.success).provideLayer(CompetitionLogging.Live.loggingLayer ++ ZEnv.live)
}
