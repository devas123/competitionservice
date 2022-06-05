package compman.compsrv.gateway.actors

import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.KafkaSupervisorCommand
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Behaviors}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.CompetitionEventsTopic
import compman.compsrv.gateway.config.{ConsumerConfig, ProducerConfig}
import compservice.model.protobuf.callback.CommandCallback
import compservice.model.protobuf.command.Command
import zio.{Tag, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

object CommandForwardingActor {

  sealed trait GatewayApiCommand

  final case class ForwardCommand(competitionId: String, body: Array[Byte]) extends GatewayApiCommand
  final case class SendCompetitionCommandAndWaitForResult(competitionId: String, body: Array[Byte])(
    val replyTo: ActorRef[CommandCallback]
  ) extends GatewayApiCommand
  final case class SendAcademyCommandAndWaitForResult(body: Array[Byte])(val replyTo: ActorRef[CommandCallback])
      extends GatewayApiCommand

  case class ActorState()
  val initialState: ActorState = ActorState()
  import Behaviors._
  def behavior[R: Tag](
                        kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
                        producerConfig: ProducerConfig,
                        consumerConfig: ConsumerConfig,
                        groupId: String,
                        callbackTimeoutMs: Int
  ): ActorBehavior[R with Logging with Clock with Console, ActorState, GatewayApiCommand] = Behaviors
    .behavior[R with Logging with Clock with Console, ActorState, GatewayApiCommand]
    .withReceive { (ctx, _, state, command, _) =>
      {
        for {
          _ <- Logging.info(s"Received academy API command $command")
          res <- command match {
            case _ @ForwardCommand(competitionId, body) => for {
                _ <- kafkaSupervisorActor ! KafkaSupervisor.PublishMessage(producerConfig.globalCommandsTopic, competitionId, body)
              } yield state
            case x: SendCompetitionCommandAndWaitForResult => for {
                cmd <- ZIO.effect(Command.parseFrom(x.body))
                _ <- ctx.make(
                  s"CallbackWaiter-${x.competitionId}-${cmd.messageInfo.flatMap(_.id).getOrElse("")}",
                  ActorConfig(),
                  CommandCallbackAggregator.initialState,
                  CommandCallbackAggregator.behavior(
                    cmd,
                    kafkaSupervisorActor,
                    CompetitionEventsTopic(consumerConfig.eventsTopicPrefix)(x.competitionId),
                    consumerConfig.callbackTopic,
                    groupId,
                    x.replyTo
                  )(callbackTimeoutMs)
                )
                _ <- kafkaSupervisorActor ! KafkaSupervisor.PublishMessage(producerConfig.globalCommandsTopic, x.competitionId, x.body)
            } yield state
            case x: SendAcademyCommandAndWaitForResult =>
              for {
                cmd <- ZIO.effect(Command.parseFrom(x.body))
                _ <- ctx.make(
                  s"AcademyCommandCallbackWaiter-${cmd.messageInfo.flatMap(_.id).getOrElse("")}",
                  ActorConfig(),
                  CommandCallbackAggregator.initialState,
                  CommandCallbackAggregator.behavior(
                    cmd,
                    kafkaSupervisorActor,
                    consumerConfig.academyNotificationsTopic,
                    consumerConfig.callbackTopic,
                    groupId,
                    x.replyTo
                  )(callbackTimeoutMs)
                )
                _ <- kafkaSupervisorActor ! KafkaSupervisor.PublishMessage(producerConfig.academyCommandsTopic, null, x.body)
              } yield state
          }
        } yield res
      }
    }
}
