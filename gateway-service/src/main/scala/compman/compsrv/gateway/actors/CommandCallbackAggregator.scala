package compman.compsrv.gateway.actors

import akka.actor.typed.{ActorRef, Behavior, MessageAdaptionFailure, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.KafkaSupervisor
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, KafkaSupervisorCommand}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.Errors
import compservice.model.protobuf.callback.{CommandCallback, CommandExecutionResult, ErrorCallback}
import compservice.model.protobuf.command.Command
import compservice.model.protobuf.event.Event

import scala.concurrent.duration.DurationInt
import scala.util.Try

object CommandCallbackAggregator {

  sealed trait CommandCallbackAggregatorCommand

  private final case object Stop                                        extends CommandCallbackAggregatorCommand
  private final case class EventReceived(event: Event)                  extends CommandCallbackAggregatorCommand
  private final case class CallbackReceived(callback: CommandCallback)  extends CommandCallbackAggregatorCommand
  private final case object CallbackComplete                            extends CommandCallbackAggregatorCommand
  private final case class Error(error: Throwable, source: String = "") extends CommandCallbackAggregatorCommand
  private final case class UnknownMessage(payload: Any)                 extends CommandCallbackAggregatorCommand

  case class ActorState(receivedEvents: Seq[Event], callback: Option[CommandCallback])

  val initialState: ActorState = ActorState(Seq.empty, None)

  private def kafkaApiMapper[T](
    forwardTo: ActorRef[CommandCallbackAggregatorCommand],
    deserialize: Array[Byte] => T,
    filter: T => Boolean,
    map: T => CommandCallbackAggregatorCommand
  ): Behavior[KafkaConsumerApi] = Behaviors.setup { ctx =>
    ctx.watch(forwardTo)
    Behaviors.receiveMessage[KafkaConsumerApi] { msg =>
      ctx.log.info(s"Received message $msg")
      msg match {
        case KafkaSupervisor.QueryStarted() =>
          forwardTo ! UnknownMessage(())
          Behaviors.same
        case KafkaSupervisor.QueryFinished(_) =>
          forwardTo ! UnknownMessage(())
          Behaviors.stopped
        case KafkaSupervisor.QueryError(error) =>
          forwardTo ! Error(error, source = ctx.self.toString)
          Behaviors.stopped
        case KafkaSupervisor.MessageReceived(_, committableRecord) =>
          val msg = Try { deserialize(committableRecord.value) }.filter(filter).map(map)
            .fold(err => Error(err, ctx.self.toString), identity)
          forwardTo ! msg
          Behaviors.same
      }
    }.receiveSignal { case (_, Terminated(_)) => Behaviors.stopped }
  }

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
      val eventReceiver = ctx.spawn(
        kafkaApiMapper[Event](
          ctx.self,
          Event.parseFrom,
          _.messageInfo.flatMap(_.correlationId).contains(commandToWaitCallbackFor.messageInfo.flatMap(_.id).get),
          EventReceived
        ),
        "Event_receiver"
      )
      val callbackReceiverAdapter = ctx.spawn(
        kafkaApiMapper[CommandCallback](
          ctx.self,
          CommandCallback.parseFrom,
          _.correlationId == commandToWaitCallbackFor.messageInfo.flatMap(_.id).get,
          CallbackReceived
        ),
        "Callback_receiver"
      )

      kafkaSupervisorActor !
        KafkaSupervisor
          .Subscribe(topic = eventsTopic, groupId = groupId, replyTo = eventReceiver, commitOffsetToKafka = true)

      kafkaSupervisorActor ! KafkaSupervisor.Subscribe(
        topic = callbackTopic,
        groupId = groupId,
        replyTo = callbackReceiverAdapter,
        commitOffsetToKafka = true
      )

      Behaviors.withTimers[CommandCallbackAggregatorCommand] { timers =>
        timers.startSingleTimer("Stop", Stop, timeoutMs.millis)
        Behaviors.receiveMessage[CommandCallbackAggregatorCommand] {
          case Stop =>
            ctx.log.info(s"Stopping after $timeoutMs milliseconds and sending a TIMEOUT response.")
            replyTo ! CommandCallback().update(
              _.command   := commandToWaitCallbackFor,
              _.result    := CommandExecutionResult.TIMEOUT,
              _.errorInfo := ErrorCallback().withMessage(s"Timeout: $timeoutMs milliseconds.")
            )
            Behaviors.stopped
          case CallbackReceived(callback) =>
            ctx.log.info(s"Received callback: $callback")
            state = state.copy(callback = Some(callback))
            if (
              callback.result != CommandExecutionResult.SUCCESS || callback.numberOfEvents <= state.receivedEvents.size
            ) { ctx.self ! CallbackComplete }
            Behaviors.same
          case CallbackComplete =>
            ctx.log.info(s"Callback complete.")
            val callbackToSend = state.callback.get.withCommand(commandToWaitCallbackFor)
              .withEvents(state.receivedEvents)
            ctx.log.info(s"Sending response: $callbackToSend")
            replyTo ! callbackToSend
            Behaviors.stopped
          case EventReceived(event) =>
            ctx.log.info(s"Received an event: $event")
            val newState = state.copy(receivedEvents = state.receivedEvents :+ event)
            if (newState.callback.exists(_.numberOfEvents == newState.receivedEvents.size)) {
              ctx.self ! CallbackComplete
            }
            state = newState
            Behaviors.same
          case Error(ex, source) =>
            ctx.log.info(s"Received an error at $source.", ex)
            replyTo !
              Commands
                .createErrorCallback(commandToWaitCallbackFor.messageInfo.flatMap(_.id), Errors.InternalException(ex))
            Behaviors.stopped
          case UnknownMessage(_) => Behaviors.same
        }.receiveSignal { case (context, MessageAdaptionFailure(exception)) =>
          context.log.error("Message adaptation failure", exception)
          Behaviors.same
        }
      }
    }
}
