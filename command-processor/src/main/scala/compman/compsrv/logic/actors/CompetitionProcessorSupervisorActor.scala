package compman.compsrv.logic.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, QueryAndSubscribe}
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior
import compman.compsrv.logic.actor.kafka.persistence.KafkaBasedEventSourcedBehavior.KafkaBasedEventSourcedBehaviorApi
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actors.CompetitionProcessorActorV2.KafkaProducerFlow
import compservice.model.protobuf.command.Command

object CompetitionProcessorSupervisorActor {

  private def updated(children: Map[String, ActorRef[KafkaBasedEventSourcedBehaviorApi]])(
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
              competitionId,
              CompetitionEventsTopic(commandProcessorConfig.eventsTopicPrefix)(competitionId),
              commandProcessorConfig.commandCallbackTopic,
              commandProcessorConfig.competitionNotificationsTopic,
              kafkaSupervisor,
              snapshotServiceFactory(commandProcessorConfig.snapshotDbPath + "/" + competitionId),
              kafkaProducerFlowOptional
            ),
            actorName
          )))
        }
        updatedChildren(actorName) ! KafkaBasedEventSourcedBehavior.CommandReceived(
          commandProcessorConfig.groupId,
          commandProcessorConfig.commandsTopic,
          competitionId = competitionId,
          command = fa,
          partition = partition,
          offset = offset
        )
        updated(updatedChildren)(commandProcessorConfig, kafkaSupervisor, snapshotServiceFactory, kafkaProducerFlowOptional)
    }
  }

  def behavior(
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
    kafkaSupervisor ! QueryAndSubscribe(
      commandProcessorConfig.commandsTopic,
      groupId = commandProcessorConfig.groupId,
      replyTo = receiver,
      commitOffsetToKafka = true
    )
    updated(Map.empty)(commandProcessorConfig, kafkaSupervisor, snapshotServiceFactory, kafkaProducerFlowOptional)
  }

  sealed trait Message

  final case class CommandReceived(competitionId: String, fa: Command, partition: Int, offset: Long) extends Message
  final case class OtherMessageReceived(payload: Any)                                                extends Message

}
