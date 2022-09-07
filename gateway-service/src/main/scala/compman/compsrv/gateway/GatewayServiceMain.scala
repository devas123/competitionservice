package compman.compsrv.gateway

import akka.actor.typed.{ActorSystem, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.{ConsumerSettings, ProducerSettings}
import cats.effect.unsafe.IORuntime
import compman.compsrv.gateway.actors.{CommandCallbackListener, CommandForwardingActor}
import compman.compsrv.gateway.config.AppConfig
import compman.compsrv.gateway.service.GatewayService
import compman.compsrv.gateway.service.GatewayService.ServiceIO
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.{CreateTopicIfMissing, KafkaTopicConfig}
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer,
  StringDeserializer,
  StringSerializer
}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder

object GatewayServiceMain extends App {

  sealed trait MainGuardianMessages

  def behavior() = Behaviors.setup[MainGuardianMessages] { context =>
    val config = AppConfig.load(context.system.settings.config)
    val consumerSettings = ConsumerSettings(context.system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(config.producer.bootstrapServers)

    val producerSettings = ProducerSettings(context.system, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(config.producer.bootstrapServers)
    val kafkaSupervisor = context.spawn(
      KafkaSupervisor.behavior(config.producer.bootstrapServers, consumerSettings, producerSettings),
      "kafkaSupervisor"
    )

    val commandCallbackListener = context.spawn(
      CommandCallbackListener.behavior(kafkaSupervisor, config.consumer.callbackTopic, config.consumer.groupId),
      "commandCallbackListener"
    )
    kafkaSupervisor ! CreateTopicIfMissing(config.consumer.callbackTopic, KafkaTopicConfig())
    val commandForwardingActor = context.spawn(
      Behaviors.supervise(CommandForwardingActor.behavior(
        kafkaSupervisorActor = kafkaSupervisor,
        commandCallbackListener = commandCallbackListener,
        producerConfig = config.producer,
        consumerConfig = config.consumer,
        config.callbackTimeoutMs
      )).onFailure(SupervisorStrategy.restart),
      "commandForwarder"
    )

    implicit val actorSystem: ActorSystem[Nothing] = context.system
    implicit val runtime: IORuntime                = IORuntime.global

    BlazeClientBuilder[ServiceIO](executionContext = actorSystem.executionContext).resource.use(client =>
      BlazeServerBuilder[ServiceIO].bindHttp(8080, "0.0.0.0").withWebSockets(true).withSocketKeepAlive(true)
        .withHttpApp(GatewayService.service(commandForwardingActor, client, config.proxy).orNotFound).serve.compile.drain
    ).unsafeRunSync()
    Behaviors.ignore
  }

  ActorSystem(behavior(), "GatewayService")
}
