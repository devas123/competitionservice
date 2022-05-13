package compman.compsrv.logic.actors

import cats.implicits._
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, PublishMessage, Subscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.Operations
import compman.compsrv.logic.logging.CompetitionLogging.{LIO, Live}
import compman.compsrv.logic.logging.info
import compman.compsrv.model.callback.{CommandCallbackDTO, CommandExecutionResult, ErrorCallbackDTO}
import compman.compsrv.model.commands.CommandDTO
import zio.clock.Clock
import zio.console.Console
import zio.logging.{LogAnnotation, Logging}

import java.util.UUID
import scala.util.Try

object StatelessCommandProcessor {
  import compman.compsrv.CommandProcessorMain.Live._
  import zio.interop.catz._

  type AcademyCommandProcessorSupervisorEnv = Logging with Clock with Console

  import Behaviors._
  def behavior(
    statelessCommandsTopic: String,
    mapper: ObjectMapper,
    groupId: String,
    statelessEventsTopic: String,
    statelessCommandCallbackTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand]
  ): ActorBehavior[AcademyCommandProcessorSupervisorEnv, Unit, AcademyCommandProcessorMessage] = Behaviors
    .behavior[AcademyCommandProcessorSupervisorEnv, Unit, AcademyCommandProcessorMessage]
    .withReceive { (_, _, _, message, _) =>
      message match {
        case AcademyCommandReceived(cmd) => for {
            processResult <- Live
              .withContext(_.annotate(LogAnnotation.CorrelationId, Option(cmd.getId).map(UUID.fromString))) {
                Operations.processStatelessCommand[LIO](cmd)
              }
            _ <- processResult match {
              case Left(value) => info(s"Error: $value") *>
                  (kafkaSupervisor ! PublishMessage(
                    statelessCommandCallbackTopic,
                    cmd.getId,
                    mapper.writeValueAsBytes(
                      new CommandCallbackDTO().setId(UUID.randomUUID().toString).setCorrelationId(cmd.getId)
                        .setResult(CommandExecutionResult.FAIL)
                        .setErrorInfo(new ErrorCallbackDTO().setMessage(s"Error: $value"))
                    )
                  ))
              case Right(events) => events.traverse(e => kafkaSupervisor ! PublishMessage(statelessEventsTopic, cmd.getId, mapper.writeValueAsBytes(e))).unit
            }
          } yield ()
      }
    }.withInit { (_, context, _, _) =>
      for {
        receiver <- context.messageAdapter[KafkaConsumerApi] {
          case KafkaSupervisor.QueryStarted()  => None
          case KafkaSupervisor.QueryFinished() => None
          case KafkaSupervisor.QueryError(_)   => None
          case KafkaSupervisor.MessageReceived(_, committableRecord) =>
            Try { mapper.readValue(committableRecord.value, classOf[CommandDTO]) }.toOption.map(AcademyCommandReceived)
        }
        _ <- kafkaSupervisor ! Subscribe(statelessCommandsTopic, groupId = groupId, replyTo = receiver)
      } yield (Seq.empty, Seq.empty, ())
    }

  sealed trait AcademyCommandProcessorMessage

  final case class AcademyCommandReceived(cmd: CommandDTO) extends AcademyCommandProcessorMessage
}
