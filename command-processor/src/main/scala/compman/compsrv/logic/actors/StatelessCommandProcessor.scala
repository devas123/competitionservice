package compman.compsrv.logic.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand, PublishMessage, Subscribe}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.Operations
import compman.compsrv.model.command.Commands
import compservice.model.protobuf.command.Command

object StatelessCommandProcessor {

  implicit val runtime: IORuntime = IORuntime.global
  import compman.compsrv.CommandProcessorMain.Live._

  def behavior(
    statelessCommandsTopic: String,
    groupId: String,
    statelessEventsTopic: String,
    commandCallbackTopic: String,
    kafkaSupervisor: ActorRef[KafkaSupervisorCommand]
  ): Behavior[AcademyCommandProcessorMessage] = Behaviors.setup { context =>
    val receiver = context.messageAdapter[KafkaConsumerApi] {
      case x @ KafkaSupervisor.QueryStarted()   => OtherMessageReceived(x)
      case x @ KafkaSupervisor.QueryFinished(_) => OtherMessageReceived(x)
      case x @ KafkaSupervisor.QueryError(_)    => OtherMessageReceived(x)
      case KafkaSupervisor.MessageReceived(_, committableRecord) =>
        AcademyCommandReceived(Command.parseFrom(committableRecord.value))
    }
    kafkaSupervisor !
      Subscribe(statelessCommandsTopic, groupId = groupId, replyTo = receiver, commitOffsetToKafka = true)

    Behaviors.receiveMessage[AcademyCommandProcessorMessage] {
      case AcademyCommandReceived(cmd) =>
        (for {
          processResult <- Operations.processStatelessCommand[IO](cmd)
          _ <- processResult match {
            case Left(value) => IO(context.log.info(s"Error: $value")) *> IO(
                kafkaSupervisor ! PublishMessage(Commands.createErrorCommandCallbackMessageParameters(
                  commandCallbackTopic,
                  Commands.correlationId(cmd),
                  value
                ))
              )
            case Right(events) => IO(events.foreach(e =>
                kafkaSupervisor !
                  PublishMessage(statelessEventsTopic, cmd.messageInfo.flatMap(_.id).orNull, e.toByteArray)
              ))
          }
        } yield ()).unsafeRunSync()
        Behaviors.same
      case OtherMessageReceived(payload) =>
        context.log.info(s"Ignoring message $payload")
        Behaviors.same
    }
  }

  sealed trait AcademyCommandProcessorMessage

  final case class AcademyCommandReceived(cmd: Command) extends AcademyCommandProcessorMessage
  final case class OtherMessageReceived(payload: Any)   extends AcademyCommandProcessorMessage
}
