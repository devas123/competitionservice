package compman.compsrv.logic.actors

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.config.CommandProcessorConfig
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, Subscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.model.commands.CommandDTO
import zio.Tag
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

import scala.util.Try

object AcademyCommandProcessorSupervisorActor {

  type AcademyCommandProcessorSupervisorEnv[Env] = Env with Logging with Clock with Console

  import Behaviors._
  def behavior[Env: Tag](
    commandProcessorOperationsFactory: CommandProcessorOperationsFactory[Env],
    commandProcessorConfig: CommandProcessorConfig,
    academyCommandsTopic: String,
    mapper: ObjectMapper,
    groupId: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand]
  ): ActorBehavior[AcademyCommandProcessorSupervisorEnv[Env], Unit, AcademyCommandProcessorMessage] = Behaviors
    .behavior[AcademyCommandProcessorSupervisorEnv[Env], Unit, AcademyCommandProcessorMessage]
    .withInit { (config, context, state, timers) =>
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

  final case class AcademyCommandReceived(fa: CommandDTO) extends AcademyCommandProcessorMessage
}
