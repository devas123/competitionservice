package compman.compsrv.logic.actors

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, Subscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.Operations
import compman.compsrv.logic.logging.CompetitionLogging.{LIO, Live}
import compman.compsrv.model.commands.CommandDTO
import zio.Tag
import zio.clock.Clock
import zio.console.Console
import zio.logging.{LogAnnotation, Logging}

import java.util.UUID
import scala.util.Try

object StatelessCommandProcessor {
  import compman.compsrv.CommandProcessorMain.Live._
  import zio.interop.catz._

  type AcademyCommandProcessorSupervisorEnv[Env] = Env with Logging with Clock with Console

  import Behaviors._
  def behavior[Env: Tag](
    academyCommandsTopic: String,
    mapper: ObjectMapper,
    groupId: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand]
  ): ActorBehavior[AcademyCommandProcessorSupervisorEnv[Env], Unit, AcademyCommandProcessorMessage] = Behaviors
    .behavior[AcademyCommandProcessorSupervisorEnv[Env], Unit, AcademyCommandProcessorMessage]
    .withReceive { (_, _, _, message, _) =>
      message match {
        case AcademyCommandReceived(cmd) => for {
            _ <- Live
              .withContext(_.annotate(LogAnnotation.CorrelationId, Option(cmd.getId).map(UUID.fromString))) {
                Operations.processStatelessCommand[LIO](cmd)
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
        _ <- kafkaSupervisor ! Subscribe(academyCommandsTopic, groupId = groupId, replyTo = receiver)
      } yield (Seq.empty, Seq.empty, ())
    }

  sealed trait AcademyCommandProcessorMessage

  final case class AcademyCommandReceived(cmd: CommandDTO) extends AcademyCommandProcessorMessage
}
