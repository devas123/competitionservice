package compman.compsrv.gateway.actors

import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{
  CreateTopicIfMissing,
  KafkaConsumerApi,
  KafkaSupervisorCommand,
  KafkaTopicConfig
}
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
  private final case object CallbackComplete                           extends CommandCallbackAggregatorCommand
  private final case class Error(error: Throwable)                     extends CommandCallbackAggregatorCommand

  case class ActorState(receivedEvents: Seq[Event], callback: Option[CommandCallback])

  val initialState: ActorState = ActorState(Seq.empty, None)

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
        _ <- kafkaSupervisorActor ! CreateTopicIfMissing(eventsTopic, KafkaTopicConfig())
        eventReceiver <- ctx.messageAdapter[KafkaConsumerApi] {
          case KafkaSupervisor.QueryStarted()    => None
          case KafkaSupervisor.QueryFinished(_)   => None
          case KafkaSupervisor.QueryError(error) => Some(Error(error))
          case KafkaSupervisor.MessageReceived(_, committableRecord) => Try { Event.parseFrom(committableRecord.value) }
              .filter(_.messageInfo.flatMap(_.correlationId).contains(
                commandToWaitCallbackFor.messageInfo.flatMap(_.id).get
              )).map(EventReceived).toOption
        }
        callbackReceiverAdapter <- ctx.messageAdapter[KafkaConsumerApi] {
          case KafkaSupervisor.QueryStarted()    => None
          case KafkaSupervisor.QueryFinished(_)   => None
          case KafkaSupervisor.QueryError(error) => Some(Error(error))
          case KafkaSupervisor.MessageReceived(_, committableRecord) => Try {
              CommandCallback.parseFrom(committableRecord.value)
            }.filter(_.correlationId == commandToWaitCallbackFor.messageInfo.flatMap(_.id).get).map(CallbackReceived)
              .toOption
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
          res <- command match {
            case Stop => for {
                _ <- Logging.debug(s"Stopping and sending a TIMEOUT response.")
                _ <- replyTo ! CommandCallback().update(
                  _.command   := commandToWaitCallbackFor,
                  _.result    := CommandExecutionResult.TIMEOUT,
                  _.errorInfo := ErrorCallback().withMessage(s"Timeout: $timeoutMs milliseconds.")
                )
                _ <- ctx.stopSelf
              } yield state
            case CallbackReceived(callback) => for {
                _ <- Logging.debug(s"Received callback: $callback")
                _ <- (ctx.self ! CallbackComplete).when(
                  callback.result != CommandExecutionResult.SUCCESS ||
                    callback.numberOfEvents <= state.receivedEvents.size
                )
              } yield state.copy(callback = Some(callback))
            case CallbackComplete => for {
                _ <- Logging.debug(s"Callback complete.")
                callbackToSend = state.callback.get.withCommand(commandToWaitCallbackFor)
                  .withEvents(state.receivedEvents)
                _ <- Logging.debug(s"Sending response: $callbackToSend")
                _ <- replyTo ! callbackToSend
                _ <- ctx.stopSelf
              } yield state
            case EventReceived(event) => for {
                _        <- Logging.debug(s"Received an event: $event")
                newState <- ZIO.effect(state.copy(receivedEvents = state.receivedEvents :+ event))
                _ <- (ctx.self ! CallbackComplete)
                  .when(newState.callback.exists(_.numberOfEvents == newState.receivedEvents.size))
              } yield newState
            case Error(ex) => for {
                _ <- replyTo ! Commands.createErrorCallback(
                  commandToWaitCallbackFor.messageInfo.flatMap(_.id),
                  Errors.InternalException(ex)
                )
                _ <- ctx.stopSelf
              } yield state
          }
        } yield res
      }
    }
}
