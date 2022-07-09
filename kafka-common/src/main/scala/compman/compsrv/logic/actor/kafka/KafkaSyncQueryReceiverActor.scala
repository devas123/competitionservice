package compman.compsrv.logic.actor.kafka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._

import scala.concurrent.{Promise, TimeoutException}
import scala.concurrent.duration.FiniteDuration

object KafkaSyncQueryReceiverActor {

  private def updated(promise: Promise[Seq[Array[Byte]]], messages: Seq[Array[Byte]]): Behavior[KafkaConsumerApi] =
    Behaviors.receiveMessage {
      case QueryStarted()                     => Behaviors.same
      case QueryFinished(_)                   => Behaviors.stopped(() => promise.success(messages))
      case QueryError(error)                  => Behaviors.stopped(() => promise.failure(error))
      case MessageReceived(_, consumerRecord) => updated(promise, messages :+ consumerRecord.value())
    }

  def behavior(promise: Promise[Seq[Array[Byte]]], timeout: FiniteDuration): Behavior[KafkaConsumerApi] = Behaviors
    .setup[KafkaConsumerApi] { _ =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer("timeout", QueryError(new TimeoutException(s"Query timeout: $timeout")), timeout)
        updated(promise, Seq.empty)
      }
    }

}
