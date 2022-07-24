package compman.compsrv.gateway.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{CreateTopicIfMissing, KafkaConsumerApi, KafkaSupervisorCommand, KafkaTopicConfig}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.Errors
import compservice.model.protobuf.callback.{CommandCallback, CommandExecutionResult, ErrorCallback}
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.event.Event

import scala.concurrent.duration.DurationInt
import scala.util.Try

object CommandCallbackAggregator {

  sealed trait CommandCallbackAggregatorCommand

  private final case object Stop                                       extends CommandCallbackAggregatorCommand
  private final case class EventReceived(event: Event)                 extends CommandCallbackAggregatorCommand
  private final case class CallbackReceived(callback: CommandCallback) extends CommandCallbackAggregatorCommand
  private final case object CallbackComplete                           extends CommandCallbackAggregatorCommand
  private final case class Error(error: Throwable)                     extends CommandCallbackAggregatorCommand
  private final case class UnknownMessage(payload: Any)                extends CommandCallbackAggregatorCommand

  case class ActorState(receivedEvents: Seq[Event], callback: Option[CommandCallback])

  val initialState: ActorState = ActorState(Seq.empty, None)

  def behavior(
    commandToWaitCallbackFor: Command,
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    eventsTopic: String,
    callbackTopic: String,
    groupId: String,
    replyTo: ActorRef[CommandCallback]
  )(timeoutMs: Int): Behavior[CommandCallbackAggregatorCommand] = Behaviors
    .setup[CommandCallbackAggregatorCommand] { ctx =>
      var state = initialState
      kafkaSupervisorActor ! CreateTopicIfMissing(eventsTopic, KafkaTopicConfig())
      val eventReceiver = ctx.messageAdapter[KafkaConsumerApi] {
        case KafkaSupervisor.QueryStarted()    => UnknownMessage(())
        case KafkaSupervisor.QueryFinished(_)  => UnknownMessage(())
        case KafkaSupervisor.QueryError(error) => Error(error)
        case KafkaSupervisor.MessageReceived(_, committableRecord) => Try { Event.parseFrom(committableRecord.value) }
            .filter(_.messageInfo.flatMap(_.correlationId).contains(
              commandToWaitCallbackFor.messageInfo.flatMap(_.id).get
            )).map(EventReceived).fold(Error, identity)
      }
      val callbackReceiverAdapter = ctx.messageAdapter[KafkaConsumerApi] {
        case KafkaSupervisor.QueryStarted()    => UnknownMessage(())
        case KafkaSupervisor.QueryFinished(_)  => UnknownMessage(())
        case KafkaSupervisor.QueryError(error) => Error(error)
        case KafkaSupervisor.MessageReceived(_, committableRecord) => Try {
            CommandCallback.parseFrom(committableRecord.value)
          }.filter(_.correlationId == commandToWaitCallbackFor.messageInfo.flatMap(_.id).get).map(CallbackReceived)
            .fold(Error, identity)
      }
      Behaviors.withTimers { timers =>
        timers.startSingleTimer("Stop", Stop, timeoutMs.millis)
        kafkaSupervisorActor ! KafkaSupervisor.QueryAndSubscribe(
          topic = eventsTopic,
          groupId = groupId,
          replyTo = eventReceiver,
          commitOffsetToKafka = true
        )
        kafkaSupervisorActor ! KafkaSupervisor.QueryAndSubscribe(
          topic = callbackTopic,
          groupId = groupId,
          replyTo = callbackReceiverAdapter,
          commitOffsetToKafka = true
        )
        Behaviors.receiveMessage {
          case Stop =>
            ctx.log.debug(s"Stopping and sending a TIMEOUT response.")
            replyTo ! CommandCallback().update(
              _.command   := commandToWaitCallbackFor,
              _.result    := CommandExecutionResult.TIMEOUT,
              _.errorInfo := ErrorCallback().withMessage(s"Timeout: $timeoutMs milliseconds.")
            )
            Behaviors.stopped(() => ())
          case CallbackReceived(callback) =>
            ctx.log.debug(s"Received callback: $callback")
            state = state.copy(callback = Some(callback))
            if (
              callback.result != CommandExecutionResult.SUCCESS || callback.numberOfEvents <= state.receivedEvents.size
            ) { ctx.self ! CallbackComplete }
            Behaviors.same
          case CallbackComplete =>
            ctx.log.debug(s"Callback complete.")
            val callbackToSend = state.callback.get.withCommand(commandToWaitCallbackFor)
              .withEvents(state.receivedEvents)
            ctx.log.debug(s"Sending response: $callbackToSend")
            replyTo ! callbackToSend
            Behaviors.stopped(() => ())
          case EventReceived(event) =>
            ctx.log.debug(s"Received an event: $event")
            val newState = state.copy(receivedEvents = state.receivedEvents :+ event)
            if (newState.callback.exists(_.numberOfEvents == newState.receivedEvents.size)) {
              ctx.self ! CallbackComplete
            }
            state = newState
            Behaviors.same
          case Error(ex) =>
            replyTo !
              Commands
                .createErrorCallback(commandToWaitCallbackFor.messageInfo.flatMap(_.id), Errors.InternalException(ex))
            Behaviors.stopped(() => ())
          case UnknownMessage(_) => Behaviors.same
        }

      }
    }
}
