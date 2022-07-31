package compman.compsrv.gateway.actors

import akka.actor.typed.{ActorRef, Behavior, MessageAdaptionFailure, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import compman.compsrv.gateway.actors.CommandCallbackListener.RegisterListener
import compman.compsrv.logic.actor.kafka.{KafkaConsumerApi, KafkaSupervisorCommand}
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi._
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand.{
  CreateTopicWithResponse,
  KafkaTopicConfig,
  PublishMessage,
  SubscribeSince
}
import compman.compsrv.model.command.Commands
import compman.compsrv.model.Errors
import compservice.model.protobuf.callback.{CommandCallback, CommandExecutionResult, ErrorCallback}
import compservice.model.protobuf.command.{Command, CommandType}
import compservice.model.protobuf.event.Event

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object CommandCallbackAggregator {
  implicit val timeout: Timeout = 3.seconds

  sealed trait CommandCallbackAggregatorCommand

  private final case object TopicCreated                                extends CommandCallbackAggregatorCommand
  private final case object Stop                                        extends CommandCallbackAggregatorCommand
  private final case class EventReceived(event: Event)                  extends CommandCallbackAggregatorCommand
  private final case class CallbackReceived(callback: CommandCallback)  extends CommandCallbackAggregatorCommand
  private final case object CallbackComplete                            extends CommandCallbackAggregatorCommand
  private final case class Error(error: Throwable, source: String = "") extends CommandCallbackAggregatorCommand
  private final case class Publish(payload: PublishMessage)             extends CommandCallbackAggregatorCommand
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
        case QueryStarted() =>
          forwardTo ! UnknownMessage(())
          Behaviors.same
        case QueryFinished(_) =>
          forwardTo ! UnknownMessage(())
          Behaviors.stopped
        case QueryError(error) =>
          forwardTo ! Error(error, source = ctx.self.toString)
          Behaviors.stopped
        case MessageReceived(_, committableRecord) =>
          val msg = Try { deserialize(committableRecord.value) }.filter(filter).map(map)
            .fold(err => Error(err, ctx.self.toString), identity)
          forwardTo ! msg
          Behaviors.same
      }
    }.receiveSignal { case (_, Terminated(_)) => Behaviors.stopped }
  }

  def behavior(
    commandToWaitCallbackFor: Command,
    messageToPublish: PublishMessage,
    commandCallbackListener: ActorRef[CommandCallbackListener.CommandCallbackListenerApi],
    kafkaSupervisorActor: ActorRef[KafkaSupervisorCommand],
    eventsTopic: String,
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

      kafkaSupervisorActor ! SubscribeSince(
        topic = eventsTopic,
        groupId = UUID.randomUUID().toString,
        replyTo = eventReceiver,
        commitOffsetToKafka = true,
        since = System.currentTimeMillis()
      )

      Behaviors.withTimers[CommandCallbackAggregatorCommand] { timers =>
        timers.startSingleTimer("Stop", Stop, timeoutMs.millis)

        if (commandToWaitCallbackFor.`type` == CommandType.CREATE_COMPETITION_COMMAND) {
          ctx.ask(
            kafkaSupervisorActor,
            resp => CreateTopicWithResponse(eventsTopic, KafkaTopicConfig(), resp)
          ) {
            case Failure(exception) => Error(error = exception, source = "Creating topic")
            case Success(value) => value match {
                case Left(error) => Error(error = error, source = "Creating topic response")
                case Right(_)    => TopicCreated
              }
          }
        } else { ctx.self ! TopicCreated }

        Behaviors.receiveMessage[CommandCallbackAggregatorCommand] {
          case TopicCreated =>
            ctx.ask(commandCallbackListener, resp => RegisterListener(callbackReceiverAdapter, resp)) {
              case Failure(exception) => Error(error = exception, source = "Registering with callback listener")
              case Success(value) => value match {
                  case Left(value) => Error(error = value, source = "Registering with callback listener")
                  case Right(_)    => Publish(messageToPublish)
                }
            }
            Behaviors.same
          case Publish(payload) =>
            kafkaSupervisorActor ! payload
            Behaviors.same
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
