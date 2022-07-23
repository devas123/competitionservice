package compman.compsrv.logic.actor.kafka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.{Promise, TimeoutException}
import scala.concurrent.duration.FiniteDuration

object KafkaSyncOffsetQueryReceiverActor {

  sealed trait KafkaSyncOffsetQueryReceiverActorApi

  case class OffsetsReceived(offsets: StartOffsetsAndTopicEndOffset) extends KafkaSyncOffsetQueryReceiverActorApi
  case class ErrorDuringMetadataRequest(exception: Throwable)        extends KafkaSyncOffsetQueryReceiverActorApi

  private def updated(
    promise: Promise[StartOffsetsAndTopicEndOffset]
  ): Behavior[KafkaSyncOffsetQueryReceiverActorApi] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case OffsetsReceived(offsets) =>
          ctx.log.info(s"Received offsets: $offsets")
          promise.success(offsets)
          Behaviors.stopped(() => ())
        case ErrorDuringMetadataRequest(exception) =>
          promise.failure(exception)
          Behaviors.stopped(() => ())
      }
    }
  }

  def behavior(
    promise: Promise[StartOffsetsAndTopicEndOffset],
    timeout: FiniteDuration
  ): Behavior[KafkaSyncOffsetQueryReceiverActorApi] = Behaviors.setup[KafkaSyncOffsetQueryReceiverActorApi] { _ =>
    Behaviors.withTimers { timers =>
      timers.startSingleTimer(
        "timeout",
        ErrorDuringMetadataRequest(new TimeoutException(s"Metadata query timeout: $timeout")),
        timeout
      )
      updated(promise)
    }
  }

}
