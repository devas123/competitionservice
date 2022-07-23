package compman.compsrv.gateway.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.KafkaSupervisorCommand
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.gateway.config.{ConsumerConfig, ProducerConfig}
import compservice.model.protobuf.callback.CommandCallback
import compservice.model.protobuf.command.Command

object CommandForwardingActor {

  sealed trait GatewayApiCommand

  final case class ForwardCommand(competitionId: String, body: Array[Byte]) extends GatewayApiCommand

  final case class SendCompetitionCommandAndWaitForResult(competitionId: String, body: Array[Byte])(
    val replyTo: ActorRef[CommandCallback]
  ) extends GatewayApiCommand

  final case class SendAcademyCommandAndWaitForResult(body: Array[Byte])(val replyTo: ActorRef[CommandCallback])
      extends GatewayApiCommand

  def behavior(
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    producerConfig: ProducerConfig,
    consumerConfig: ConsumerConfig,
    groupId: String,
    callbackTimeoutMs: Int
  ): Behavior[GatewayApiCommand] = Behaviors.setup[GatewayApiCommand] { ctx =>
    Behaviors.receiveMessage { command =>
      ctx.log.info(s"Received academy API command $command")
      command match {
        case _ @ForwardCommand(competitionId, body) =>
          kafkaSupervisorActor ! KafkaSupervisor.PublishMessage(producerConfig.globalCommandsTopic, competitionId, body)
          Behaviors.same
        case x: SendCompetitionCommandAndWaitForResult =>
          val cmd = Command.parseFrom(x.body)
          ctx.spawn(
            CommandCallbackAggregator.behavior(
              cmd,
              kafkaSupervisorActor,
              CompetitionEventsTopic(consumerConfig.eventsTopicPrefix)(x.competitionId),
              consumerConfig.callbackTopic,
              groupId,
              x.replyTo
            )(callbackTimeoutMs),
            s"CallbackWaiter-${x.competitionId}-${cmd.messageInfo.flatMap(_.id).getOrElse("")}"
          )
          kafkaSupervisorActor !
            KafkaSupervisor.PublishMessage(producerConfig.globalCommandsTopic, x.competitionId, x.body)
          Behaviors.same
        case x: SendAcademyCommandAndWaitForResult =>
          val cmd = Command.parseFrom(x.body)
          ctx.spawn(
            CommandCallbackAggregator.behavior(
              cmd,
              kafkaSupervisorActor,
              consumerConfig.academyNotificationsTopic,
              consumerConfig.callbackTopic,
              groupId,
              x.replyTo
            )(callbackTimeoutMs),
            s"AcademyCommandCallbackWaiter-${cmd.messageInfo.flatMap(_.id).getOrElse("")}"
          )
          kafkaSupervisorActor ! KafkaSupervisor.PublishMessage(producerConfig.academyCommandsTopic, null, x.body)
          Behaviors.same
      }
    }
  }
}
