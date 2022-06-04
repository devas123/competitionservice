package compman.compsrv.logic.actors

import cats.implicits._
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{
  KafkaConsumerApi,
  KafkaSupervisorCommand,
  PublishMessage,
  Subscribe
}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.Operations
import compman.compsrv.logic.logging.CompetitionLogging.{Annotations, LIO, Live}
import compman.compsrv.logic.logging.info
import compman.compsrv.model.command.Commands
import compservice.model.protobuf.command.Command
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

import scala.util.Try

object StatelessCommandProcessor {
  import compman.compsrv.CommandProcessorMain.Live._
  import zio.interop.catz._

  type AcademyCommandProcessorSupervisorEnv = Logging with Clock with Console

  import Behaviors._
  def behavior(
    statelessCommandsTopic: String,
    groupId: String,
    statelessEventsTopic: String,
    commandCallbackTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand]
  ): ActorBehavior[AcademyCommandProcessorSupervisorEnv, Unit, AcademyCommandProcessorMessage] = Behaviors
    .behavior[AcademyCommandProcessorSupervisorEnv, Unit, AcademyCommandProcessorMessage]
    .withReceive { (_, _, _, message, _) =>
      message match {
        case AcademyCommandReceived(cmd) => for {
            processResult <- Live
              .withContext(_.annotate(Annotations.correlationId, cmd.messageInfo.flatMap(_.correlationId))) {
                Operations.processStatelessCommand[LIO](cmd)
              }
            _ <- processResult match {
              case Left(value) => info(s"Error: $value") *>
                  (kafkaSupervisor ! PublishMessage(Commands.createErrorCommandCallbackMessageParameters(
                    commandCallbackTopic,
                    Commands.correlationId(cmd),
                    value
                  )))
              case Right(events) => events.traverse(e =>
                  kafkaSupervisor !
                    PublishMessage(statelessEventsTopic, cmd.messageInfo.flatMap(_.id).orNull, e.toByteArray)
                ).unit
            }
          } yield ()
      }
    }.withInit { (_, context, _, _) =>
      for {
        receiver <- context.messageAdapter[KafkaConsumerApi] {
          case KafkaSupervisor.QueryStarted()  => None
          case KafkaSupervisor.QueryFinished(_) => None
          case KafkaSupervisor.QueryError(_)   => None
          case KafkaSupervisor.MessageReceived(_, committableRecord) =>
            Try { Command.parseFrom(committableRecord.value) }.toOption.map(AcademyCommandReceived)
        }
        _ <- kafkaSupervisor ! Subscribe(statelessCommandsTopic, groupId = groupId, replyTo = receiver)
      } yield (Seq.empty, Seq.empty, ())
    }

  sealed trait AcademyCommandProcessorMessage

  final case class AcademyCommandReceived(cmd: Command) extends AcademyCommandProcessorMessage
}
