package compman.compsrv.logic.actor.kafka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi.{MessageReceived, QueryError, QueryFinished, QueryStarted}

import scala.concurrent.{Promise, TimeoutException}
import scala.concurrent.duration.FiniteDuration

object KafkaSyncQueryReceiverActor {

  private def updated(promise: Promise[Seq[Array[Byte]]], messages: Seq[Array[Byte]]): Behavior[KafkaConsumerApi] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case QueryStarted() =>
          ctx.log.info(s"Query started.")
          Behaviors.same
        case QueryFinished(_) =>
          ctx.log.info(s"Query finished.")
          promise.success(messages)
          Behaviors.stopped
        case QueryError(error) => Behaviors.stopped(() => promise.failure(error))
        case MessageReceived(topic, consumerRecord) =>
          ctx.log.info(
            s"Received message from topic $topic with offset ${consumerRecord.offset()} and partition ${consumerRecord.partition()}"
          )
          updated(promise, messages :+ consumerRecord.value())
      }
    }
  }

  def behavior(promise: Promise[Seq[Array[Byte]]], timeout: FiniteDuration): Behavior[KafkaConsumerApi] = Behaviors
    .setup[KafkaConsumerApi] { _ =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer("timeout", QueryError(new TimeoutException(s"Query timeout: $timeout")), timeout)
        updated(promise, Seq.empty)
      }
    }

}
