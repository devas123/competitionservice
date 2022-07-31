package compman.compsrv.logic.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.ProducerSettings
import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.logic.actor.kafka.{KafkaConsumerApi, KafkaSupervisorCommand}
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.{
  KafkaBasedEventSourcedBehaviorApi,
  KafkaProducerFlow
}
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi.{MessageReceived, QueryError, QueryFinished, QueryStarted}
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.SubscribeToEnd
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
      case CompetitionProcessorStopped(id) => updated(children - id)(
          producerSettings,
          commandProcessorConfig,
          kafkaSupervisor,
          snapshotServiceFactory,
          kafkaProducerFlowOptional
        )
      case CommandReceived(competitionId, fa, partition, _) =>
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
        val actor = updatedChildren(actorName)
        context.watchWith(actor, CompetitionProcessorStopped(actorName))
        actor ! KafkaBasedEventSourcedBehavior.CommandReceived(
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
      case x @ QueryStarted()   => OtherMessageReceived(x)
      case x @ QueryFinished(_) => OtherMessageReceived(x)
      case x @ QueryError(_)    => OtherMessageReceived(x)
      case MessageReceived(_, committableRecord) =>
        val command = Command.parseFrom(committableRecord.value)
        CommandReceived(committableRecord.key(), command, committableRecord.partition(), committableRecord.offset())
    }
    kafkaSupervisor ! SubscribeToEnd(
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

  private final case class CompetitionProcessorStopped(id: String) extends Message

}
