package compman.compsrv.query
import cats.effect
import com.mongodb.connection.ClusterSettings
import compman.compsrv.logic.actors.ActorSystem
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.{CompetitionApiActor, CompetitionEventListener, CompetitionEventListenerSupervisor, WebsocketConnectionSupervisor}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import compman.compsrv.query.config.AppConfig
import compman.compsrv.query.service.{CompetitionHttpApiService, WebsocketService}
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import compman.compsrv.query.service.kafka.EventStreamingService
import fs2.concurrent.SignallingRef
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import zio.interop.catz._
import zio.kafka.consumer.ConsumerSettings
import zio.logging.Logging

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

object QueryServiceMain extends zio.App {

  import org.mongodb.scala._


  def server(args: List[String]): ZIO[zio.ZEnv with Clock with Logging, Throwable, Unit] = for {
    (config, mongodbConfig) <- AppConfig.load()
    credential <- ZIO.effect(
      MongoCredential.createCredential(mongodbConfig.username, mongodbConfig.authenticationDb, mongodbConfig.password.toCharArray)
    )
    mongoDbSession <- ZIO.effect(
      MongoClient(
        MongoClientSettings.builder()
          .credential(credential)
          .applyToClusterSettings((builder: ClusterSettings.Builder) => builder.hosts(List(new ServerAddress(mongodbConfig.host)).asJava))
          .build())
    )
    actorSystem <- ActorSystem("queryServiceActorSystem")
    webSocketSupervisor <- actorSystem.make(
      "webSocketSupervisor",
      ActorConfig(),
      WebsocketConnectionSupervisor.initialState,
      WebsocketConnectionSupervisor.behavior[ZEnv]
    )
    _ <- actorSystem.make(
      "competitionEventListenerSupervisor",
      ActorConfig(),
      (),
      CompetitionEventListenerSupervisor.behavior(
        EventStreamingService.live(ConsumerSettings(config.consumer.brokers).withGroupId(config.consumer.groupId)),
        config.competitionEventListener.competitionNotificationsTopic,
        CompetitionEventListenerSupervisor.Live(mongoDbSession, mongodbConfig),
        CompetitionEventListener.Live(mongoDbSession, mongodbConfig),
        webSocketSupervisor
      )
    )
    competitionApiActor <- actorSystem.make(
      "queryApiActor",
      ActorConfig(),
      CompetitionApiActor.initialState,
      CompetitionApiActor.behavior[ZEnv](CompetitionApiActor.Live(mongoDbSession, mongodbConfig))
    )
    signal <- SignallingRef[ServiceIO, Boolean](false)
    _ <- (for {
      _ <- args.headOption.map(f => if (Files.exists(Path.of(f))) ZIO.unit else signal.set(true)).getOrElse(ZIO.unit)
      _ <- ZIO.sleep(5.seconds)
    } yield ()).forever.fork
    _        <- Logging.debug("Starting server...")
    exitCode <- effect.Ref.of[ServiceIO, effect.ExitCode](effect.ExitCode.Success)
    serviceVersion = "v1"
    httpApp = Router[ServiceIO](
      s"/query/$serviceVersion"    -> CompetitionHttpApiService.service(competitionApiActor),
      s"/query/$serviceVersion/ws" -> WebsocketService.wsRoutes(webSocketSupervisor)
    ).orNotFound
    srv <- ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[ServiceIO].bindHttp(9000, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
        .withHttpApp(httpApp).serveWhile(signal, exitCode).compile.drain
    }
  } yield srv

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server(args).tapError(logError)
    .fold(_ => ExitCode.failure, _ => ExitCode.success).provideLayer(CompetitionLogging.Live.loggingLayer ++ ZEnv.live)
}
