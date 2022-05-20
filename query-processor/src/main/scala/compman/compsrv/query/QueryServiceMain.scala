package compman.compsrv.query
import com.mongodb.connection.ClusterSettings
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.logic.actors.ActorSystem
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.behavior.{CompetitionEventListener, CompetitionEventListenerSupervisor, StatelessEventListener, WebsocketConnectionSupervisor}
import compman.compsrv.logic.actors.behavior.api.{AcademyApiActor, CompetitionApiActor}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.logError
import compman.compsrv.query.config.AppConfig
import compman.compsrv.query.service.{CompetitionHttpApiService, WebsocketService}
import compman.compsrv.query.service.CompetitionHttpApiService.ServiceIO
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import zio._
import zio.clock.Clock
import zio.interop.catz._
import zio.logging.Logging

import scala.jdk.CollectionConverters._

object QueryServiceMain extends zio.App {

  import org.mongodb.scala._

  def server(): ZIO[zio.ZEnv with Clock with Logging, Throwable, Unit] =
    ActorSystem("queryServiceActorSystem").use { actorSystem =>
      for {
        (config, mongodbConfig) <- AppConfig.load()
        credential <- ZIO.effect(MongoCredential.createCredential(
          mongodbConfig.username,
          mongodbConfig.authenticationDb,
          mongodbConfig.password.toCharArray
        ))
        mongoDbSession <- ZIO.effect(MongoClient(
          MongoClientSettings.builder().credential(credential)
            .applyToClusterSettings((builder: ClusterSettings.Builder) => {
              builder.hosts(List(new ServerAddress(mongodbConfig.host)).asJava)
              ()
            }).build()
        ))
        webSocketSupervisor <- actorSystem.make(
          "webSocketSupervisor",
          ActorConfig(),
          WebsocketConnectionSupervisor.initialState,
          WebsocketConnectionSupervisor.behavior[ZEnv]
        )
        kafkaSupervisor <- actorSystem
          .make("kafkaSupervisor", ActorConfig(), None, KafkaSupervisor.behavior[ZEnv](config.consumer.brokers))
        _ <- actorSystem.make(
          "competitionEventListenerSupervisor",
          ActorConfig(),
          (),
          CompetitionEventListenerSupervisor.behavior[ZEnv](
            config.competitionEventListener.competitionNotificationsTopic,
            config.statelessEventListener.commandCallbackTopic,
            CompetitionEventListenerSupervisor.Live(mongoDbSession, mongodbConfig),
            kafkaSupervisor,
            CompetitionEventListener.Live(mongoDbSession, mongodbConfig),
            webSocketSupervisor
          )
        )
        _ <- kafkaSupervisor !
          CreateTopicIfMissing(config.statelessEventListener.commandCallbackTopic, KafkaTopicConfig())
        _ <- kafkaSupervisor !
          CreateTopicIfMissing(config.statelessEventListener.academyNotificationsTopic, KafkaTopicConfig())
        competitionApiActor <- actorSystem.make(
          "queryApiActor",
          ActorConfig(),
          CompetitionApiActor.initialState,
          CompetitionApiActor.behavior[ZEnv](CompetitionApiActor.Live(mongoDbSession, mongodbConfig))
        )
        academyApiActor <- actorSystem.make(
          "academyApiActor",
          ActorConfig(),
          AcademyApiActor.initialState,
          AcademyApiActor.behavior[ZEnv](AcademyApiActor.Live(mongoDbSession, mongodbConfig))
        )
        _ <- actorSystem.make(
          "stateless-event-listener",
          ActorConfig(),
          (),
          StatelessEventListener.behavior[ZEnv](
            config.statelessEventListener,
            StatelessEventListener.Live(mongoClient = mongoDbSession, mongodbConfig = mongodbConfig),
            kafkaSupervisor
          )
        )
        _        <- Logging.debug("Starting server...")
        serviceVersion = "v1"
        httpApp = Router[ServiceIO](
          s"/query/$serviceVersion"    -> CompetitionHttpApiService.service(competitionApiActor, academyApiActor),
          s"/query/$serviceVersion/ws" -> WebsocketService.wsRoutes(webSocketSupervisor)
        ).orNotFound
        srv <- ZIO.runtime[ZEnv] *> {
          BlazeServerBuilder[ServiceIO].bindHttp(9000, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
            .withHttpApp(httpApp).serve.compile.drain
        }
      } yield srv
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = server().tapError(logError).exitCode
    .provideLayer(CompetitionLogging.Live.loggingLayer ++ ZEnv.live)
}
