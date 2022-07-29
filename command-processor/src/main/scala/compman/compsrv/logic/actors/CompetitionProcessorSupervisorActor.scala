package compman.compsrv.logic.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.ProducerSettings
import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, Subscribe}
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.{
  KafkaBasedEventSourcedBehaviorApi,
  KafkaProducerFlow
}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compservice.model.protobuf.command.Command

object CompetitionProcessorSupervisorActor {

  private def updated(children: Map[String, ActorRef[KafkaBasedEventSourcedBehaviorApi]])(
    producerSettings: ProducerSettings[String, Array[Byte]],
    commandProcessorConfig: CommandProcessorConfig,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    snapshotServiceFactory: String => SnapshotService.Service,
    kafkaProducerFlowOptional: Option[KafkaProducerFlow]
  ): Behavior[Message] = Behaviors.receive { (context, command) =>
    command match {
      case OtherMessageReceived(payload) =>
        context.log.debug(s"Received message $payload")
        Behaviors.same
      case CommandReceived(competitionId, fa, partition, offset) =>
        val actorName = s"CompetitionProcessor-$competitionId"
        context.log.info(s"Received command: $fa")
        val updatedChildren = children.updatedWith(actorName) { optActor =>
          optActor.orElse(Some(context.spawn(
            CompetitionProcessorActorV2.behavior(
              competitionId = competitionId,
              eventsTopic = CompetitionEventsTopic(commandProcessorConfig.eventsTopicPrefix)(competitionId),
              commandCallbackTopic = commandProcessorConfig.commandCallbackTopic,
              competitionNotificationsTopic = commandProcessorConfig.competitionNotificationsTopic,
              producerSettings = producerSettings,
              kafkaSupervisor = kafkaSupervisor,
              snapshotService = snapshotServiceFactory(commandProcessorConfig.snapshotDbPath + "/" + competitionId),
              kafkaProducerFlowOptional = kafkaProducerFlowOptional
            ),
            actorName
          )))
        }
        updatedChildren(actorName) ! KafkaBasedEventSourcedBehavior.CommandReceived(
          commandProcessorConfig.commandsTopic,
          competitionId = competitionId,
          command = fa,
          partition = partition
        )
        updated(updatedChildren)(
          producerSettings,
          commandProcessorConfig,
          kafkaSupervisor,
          snapshotServiceFactory,
          kafkaProducerFlowOptional
        )
    }
  }

  def behavior(
    producerSettings: ProducerSettings[String, Array[Byte]],
    commandProcessorConfig: CommandProcessorConfig,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand],
    snapshotServiceFactory: String => SnapshotService.Service,
    kafkaProducerFlowOptional: Option[KafkaProducerFlow] = None
  ): Behavior[Message] = Behaviors.setup { ctx =>
    val receiver = ctx.messageAdapter[KafkaConsumerApi] {
      case x @ KafkaSupervisor.QueryStarted()   => OtherMessageReceived(x)
      case x @ KafkaSupervisor.QueryFinished(_) => OtherMessageReceived(x)
      case x @ KafkaSupervisor.QueryError(_)    => OtherMessageReceived(x)
      case KafkaSupervisor.MessageReceived(_, committableRecord) =>
        val command = Command.parseFrom(committableRecord.value)
        CommandReceived(committableRecord.key(), command, committableRecord.partition(), committableRecord.offset())
    }
    kafkaSupervisor ! Subscribe(
      commandProcessorConfig.commandsTopic,
      groupId = commandProcessorConfig.groupId,
      replyTo = receiver,
      commitOffsetToKafka = true
    )
    updated(Map.empty)(
      producerSettings,
      commandProcessorConfig,
      kafkaSupervisor,
      snapshotServiceFactory,
      kafkaProducerFlowOptional
    )
  }

  sealed trait Message

  final case class CommandReceived(competitionId: String, fa: Command, partition: Int, offset: Long) extends Message
  final case class OtherMessageReceived(payload: Any)                                                extends Message

}
