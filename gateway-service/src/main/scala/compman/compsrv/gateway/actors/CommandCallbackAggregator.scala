package compman.compsrv.gateway.actors

import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand}
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Behaviors}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.Errors
import compservice.model.protobuf.callback.{CommandCallback, CommandExecutionResult, ErrorCallback}
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.event.Event
import zio.{Tag, ZIO}
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.logging.Logging

import scala.util.Try

object CommandCallbackAggregator {

  sealed trait CommandCallbackAggregatorCommand

  private final case object Stop                                       extends CommandCallbackAggregatorCommand
  private final case class EventReceived(event: Event)                 extends CommandCallbackAggregatorCommand
  private final case class CallbackReceived(callback: CommandCallback) extends CommandCallbackAggregatorCommand
  private final case class Error(error: Throwable)                     extends CommandCallbackAggregatorCommand

  case class ActorState(receivedEvents: Seq[Event])

  val initialState: ActorState = ActorState(Seq.empty)

  import Behaviors._

  def behavior[R: Tag](
    commandToWaitCallbackFor: Command,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    eventsTopic: String,
    callbackTopic: String,
    groupId: String,
    replyTo: ActorRef[CommandCallback]
  )(
    timeoutMs: Int
  ): ActorBehavior[R with Logging with Clock with Console, ActorState, CommandCallbackAggregatorCommand] = Behaviors
    .behavior[R with Logging with Clock with Console, ActorState, CommandCallbackAggregatorCommand]
    .withInit { (_, ctx, state, timers) =>
      for {
        eventReceiver <- ctx.messageAdapter[KafkaConsumerApi] {
          case KafkaSupervisor.QueryStarted()    => None
          case KafkaSupervisor.QueryFinished()   => None
          case KafkaSupervisor.QueryError(error) => Some(Error(error))
          case KafkaSupervisor.MessageReceived(_, committableRecord) => Try { Event.parseFrom(committableRecord.value) }
              .map(EventReceived).toOption
        }
        callbackReceiverAdapter <- ctx.messageAdapter[KafkaConsumerApi] {
          case KafkaSupervisor.QueryStarted()    => None
          case KafkaSupervisor.QueryFinished()   => None
          case KafkaSupervisor.QueryError(error) => Some(Error(error))
          case KafkaSupervisor.MessageReceived(_, committableRecord) =>
            Try { CommandCallback.parseFrom(committableRecord.value) }.map(CallbackReceived).toOption
        }
        _ <- timers.startSingleTimer("Stop", timeoutMs.millis, Stop)
        _ <- kafkaSupervisorActor !
          KafkaSupervisor.Subscribe(topic = eventsTopic, groupId = groupId, replyTo = eventReceiver)
        _ <- kafkaSupervisorActor !
          KafkaSupervisor.Subscribe(topic = callbackTopic, groupId = groupId, replyTo = callbackReceiverAdapter)
      } yield (Seq.empty, Seq.empty, state)
    }.withReceive { (ctx, _, state, command, _) =>
      {
        for {
          _ <- Logging.info(s"Received academy API command $command")
          res <- command match {
            case Stop => for {
                _ <- replyTo ! CommandCallback().update(
                  _.id.setIfDefined(commandToWaitCallbackFor.messageInfo.map(_.id)),
                  _.command   := commandToWaitCallbackFor,
                  _.result    := CommandExecutionResult.TIMEOUT,
                  _.errorInfo := ErrorCallback().withMessage(s"Timeout: $timeoutMs milliseconds.")
                )
                _ <- ctx.stopSelf
              } yield state
            case CallbackReceived(callback) => for {
              _ <- replyTo ! callback.withCommand(commandToWaitCallbackFor)
                .withEvents(state.receivedEvents)
              _ <- ctx.stopSelf
            } yield state
            case EventReceived(event) => ZIO.effect(state.copy(receivedEvents = state.receivedEvents :+ event))
            case Error(ex) =>
              for {
                _ <- replyTo ! Commands.createErrorCallback(commandToWaitCallbackFor.messageInfo.map(_.id), Errors.InternalException(ex))
                _ <- ctx.stopSelf
              } yield state
          }
        } yield res
      }
    }
}
