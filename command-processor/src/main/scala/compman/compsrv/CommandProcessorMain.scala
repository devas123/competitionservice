package compman.compsrv

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, SupervisorStrategy}
import akka.kafka.{ConsumerSettings, ProducerSettings}
import cats.effect.IO
import compman.compsrv.config.AppConfig
import compman.compsrv.logic.Operations._
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaTopicConfig}
import compman.compsrv.logic.actors._
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.Mapping
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}

object CommandProcessorMain extends App {

  sealed trait MainActorApi

  object Live {
    implicit val commandMapping: Mapping.CommandMapping[IO] = Mapping.CommandMapping.live
    implicit val eventMapping: Mapping.EventMapping[IO]     = model.Mapping.EventMapping.live
    implicit val idOperations: IdOperations[IO]             = IdOperations.live
    implicit val eventOperations: EventOperations[IO]       = EventOperations.live
    implicit val selectInterpreter: Interpreter[IO]         = Interpreter.asTask[IO]
  }

  def commandProcessorMainBehavior() = Behaviors.setup[MainActorApi] { context =>
    val appConfig = AppConfig.load(context.system.settings.config)
    val consumerSettings = ConsumerSettings.create(context.system, new StringDeserializer, new ByteArrayDeserializer)
    val producerSettings = ProducerSettings.create(context.system, new StringSerializer, new ByteArraySerializer)
    val kafkaSupervisor = context.spawn(Behaviors.supervise(KafkaSupervisor.behavior(appConfig.consumer.brokers.mkString(","),consumerSettings, producerSettings)).onFailure(SupervisorStrategy.restart), "kafkaSupervisor")
   kafkaSupervisor !
    CreateTopicIfMissing(appConfig.commandProcessor.commandCallbackTopic, KafkaTopicConfig())
   kafkaSupervisor !
      CreateTopicIfMissing(appConfig.commandProcessor.competitionNotificationsTopic, KafkaTopicConfig())
   kafkaSupervisor !
      CreateTopicIfMissing(appConfig.commandProcessor.academyNotificationsTopic, KafkaTopicConfig())
    context.spawn(Behaviors.supervise(CompetitionProcessorSupervisorActor.behavior(appConfig.commandProcessor, kafkaSupervisor))
      .onFailure(SupervisorStrategy.restart.withStopChildren(false)), "competition-command-processor-supervisor")
    context.spawn(Behaviors.supervise(StatelessCommandProcessor.behavior(
      appConfig.consumer.commandTopics.academy,
      appConfig.consumer.groupId,
      appConfig.commandProcessor.academyNotificationsTopic,
      appConfig.commandProcessor.commandCallbackTopic,
      kafkaSupervisor
    )).onFailure(SupervisorStrategy.restart.withStopChildren(false)), "competition-command-processor-supervisor")
    Behaviors.ignore[MainActorApi]
  }

  ActorSystem(commandProcessorMainBehavior(), "CommandProcessorMain")
}
