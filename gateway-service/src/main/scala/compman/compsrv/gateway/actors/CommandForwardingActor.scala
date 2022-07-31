package compman.compsrv.gateway.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.gateway.config.{ConsumerConfig, ProducerConfig}
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.PublishMessage
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
    commandCallbackListener: ActorRef[CommandCallbackListener.CommandCallbackListenerApi],
    producerConfig: ProducerConfig,
    consumerConfig: ConsumerConfig,
    callbackTimeoutMs: Int
  ): Behavior[GatewayApiCommand] = Behaviors.setup[GatewayApiCommand] { ctx =>
    Behaviors.receiveMessage { command =>
      ctx.log.info(s"Received API command $command")
      command match {
        case _ @ForwardCommand(competitionId, body) =>
          kafkaSupervisorActor ! PublishMessage(producerConfig.globalCommandsTopic, competitionId, body)
          Behaviors.same
        case x: SendCompetitionCommandAndWaitForResult =>
          val cmd = Command.parseFrom(x.body)
          ctx.spawn(
            CommandCallbackAggregator.behavior(
              cmd,
              PublishMessage(producerConfig.globalCommandsTopic, x.competitionId, x.body),
              commandCallbackListener,
              kafkaSupervisorActor,
              CompetitionEventsTopic(consumerConfig.eventsTopicPrefix)(x.competitionId),
              x.replyTo
            )(callbackTimeoutMs),
            s"CallbackWaiter-${x.competitionId}-${cmd.messageInfo.flatMap(_.id).getOrElse("")}"
          )
          Behaviors.same
        case x: SendAcademyCommandAndWaitForResult =>
          val cmd = Command.parseFrom(x.body)
          ctx.spawn(
            CommandCallbackAggregator.behavior(
              cmd,
              PublishMessage(producerConfig.academyCommandsTopic, null, x.body),
              commandCallbackListener,
              kafkaSupervisorActor,
              consumerConfig.academyNotificationsTopic,
              x.replyTo
            )(callbackTimeoutMs),
            s"AcademyCommandCallbackWaiter-${cmd.messageInfo.flatMap(_.id).getOrElse("")}"
          )
          Behaviors.same
      }
    }
  }
}
