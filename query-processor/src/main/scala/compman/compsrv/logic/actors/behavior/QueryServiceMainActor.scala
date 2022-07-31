package compman.compsrv.logic.actors.behavior

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.mongodb.connection.ClusterSettings
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.logic.actors.behavior.api.{AcademyApiActor, CompetitionApiActor}
import compman.compsrv.query.config.AppConfig
import compman.compsrv.query.service.{QueryHttpApiService, WebsocketService}
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential, ServerAddress}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

object QueryServiceMainActor {

  sealed trait QueryServiceMainActorApi

  def behavior(): Behavior[QueryServiceMainActorApi] = Behaviors.setup[QueryServiceMainActorApi] { context =>
    val config                     = context.system.settings.config
    val (appConfig, mongodbConfig) = AppConfig.load(config)
    val credential = MongoCredential
      .createCredential(mongodbConfig.username, mongodbConfig.authenticationDb, mongodbConfig.password.toCharArray)

    val mongodbSession = MongoClient(
      MongoClientSettings.builder().credential(credential)
        .applyToClusterSettings((builder: ClusterSettings.Builder) => {
          builder.hosts(List(new ServerAddress(mongodbConfig.host)).asJava)
          ()
        }).build()
    )

    val webSocketSupervisor = context.spawn(
      Behaviors.supervise(WebsocketConnectionSupervisor.behavior()).onFailure(SupervisorStrategy.restart),
      "webSocketSupervisor"
    )
    val bootstrapServers = appConfig.consumer.brokers.mkString(",")

    val producerConfig = config.getConfig("akka.kafka.producer")
    val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(bootstrapServers)
    val consumerConfig = config.getConfig("akka.kafka.consumer")
    val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers).withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

    val kafkaSupervisor = context.spawn(
      Behaviors.supervise(KafkaSupervisor.behavior(bootstrapServers, consumerSettings, producerSettings))
        .onFailure(SupervisorStrategy.restartWithBackoff(1.seconds, 30.seconds, 0.5)),
      "kafkaSupervisor"
    )

    val competitionEventListenerBehavior = CompetitionEventListenerSupervisor.behavior(
      appConfig.competitionEventListener.competitionNotificationsTopic,
      appConfig.statelessEventListener.commandCallbackTopic,
      CompetitionEventListenerSupervisor.Live(mongodbSession, mongodbConfig),
      kafkaSupervisor,
      CompetitionEventListener.Live(mongodbSession, mongodbConfig),
      webSocketSupervisor
    )
    context.spawn(
      Behaviors.supervise(competitionEventListenerBehavior).onFailure(SupervisorStrategy.restart),
      "CompetitionEventListenerSupervisor"
    )
    kafkaSupervisor ! CreateTopicIfMissing(appConfig.statelessEventListener.commandCallbackTopic, KafkaTopicConfig())
    kafkaSupervisor !
      CreateTopicIfMissing(appConfig.statelessEventListener.academyNotificationsTopic, KafkaTopicConfig())

    val competitionApiActor = context.spawn(
      Behaviors.supervise(CompetitionApiActor.behavior(CompetitionApiActor.Live(mongodbSession, mongodbConfig)))
        .onFailure(SupervisorStrategy.restart),
      "competitionQueryApi"
    )
    val academyApiActor = context.spawn(
      Behaviors.supervise(AcademyApiActor.behavior(AcademyApiActor.Live(mongodbSession, mongodbConfig)))
        .onFailure(SupervisorStrategy.restart),
      "academyQueryApi"
    )
    context.spawn(
      Behaviors.supervise(StatelessEventListener.behavior(
        appConfig.statelessEventListener,
        StatelessEventListener.Live(mongoClient = mongodbSession, mongodbConfig = mongodbConfig),
        kafkaSupervisor
      )).onFailure(SupervisorStrategy.restart),
      "statelessEventListener"
    )

    implicit val actorSystem: ActorSystem[Nothing] = context.system
    val serviceVersion       = "v1"
    val httpApp = Router[ServiceIO](
      s"/query/$serviceVersion"    -> QueryHttpApiService.service(competitionApiActor, academyApiActor),
      s"/query/$serviceVersion/ws" -> WebsocketService.wsRoutes(webSocketSupervisor)
    ).orNotFound

    val srv = BlazeServerBuilder[ServiceIO].bindHttp(9000, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
      .withHttpApp(httpApp).serve.compile.drain

    context.spawn(QueryHttpServiceRunner.behavior(srv), "QueryHttpServiceRunner")
    Behaviors.ignore
  }

}
