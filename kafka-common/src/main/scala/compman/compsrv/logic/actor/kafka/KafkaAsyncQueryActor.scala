package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi.{QueryFinished, QueryStarted}
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt

private class KafkaAsyncQueryActor(
  context: ActorContext[KafkaSubscribeActor.KafkaSubscribeActorApi],
  consumerSettings: ConsumerSettings[String, Array[Byte]],
  topic: String,
  replyTo: ActorRef[KafkaConsumerApi],
  endOffset: Map[Int, Long]
)(implicit val materializer: Materializer)
    extends QuerySubscribeBase(context, consumerSettings) {
  implicit val dispatcher: ExecutionContextExecutor = context.executionContext
  context.watchWith(replyTo, Stop)
  replyTo ! QueryStarted()

  override def onSignal: PartialFunction[Signal, Behavior[KafkaSubscribeActorApi]] = { case _: PostStop =>
    stopConsumer()
    Behaviors.same
  }

  override def onMessage(
    msg: KafkaSubscribeActor.KafkaSubscribeActorApi
  ): Behavior[KafkaSubscribeActor.KafkaSubscribeActorApi] = {
    msg match {
      case ForwardMessage(msg, to) =>
        context.log.warn(s"Query is not yet started, received unexpected ForwardMessage($msg, $to).")
        Behaviors.same
      case Stop =>
        replyTo ! QueryFinished(0L)
        Behaviors.stopped
      case FailureInOffsetsRetrieval(msg, exception) => logMessage(msg, exception, replyTo)
      case Start(offsets, _) => Behaviors.setup { ctx =>
          val startOffsets         = offsets.startOffsets
          val numberOfEventsToTake = offsets.endOffsets.map { e => e._2 - startOffsets(e._1) }.sum
          val endOffsets           = offsets.endOffsets.map(e => e._1.partition() -> e._2)
          ctx.log.info(
            s"Starting query with start offset $startOffsets, end offsets: $endOffsets, number of events to take: $numberOfEventsToTake"
          )
          if (numberOfEventsToTake > 0) {
            val subscription                      = Subscriptions.assignmentWithOffset(startOffsets)
            val (consumerControl, streamFinished) = startConsumerStream(subscription, replyTo)
            this.consumerControl = Some(consumerControl)
            ctx.pipeToSelf(streamFinished)(_ => Stop)
            var messageCount = 0L
            context.setReceiveTimeout(1.seconds, Stop)
            Behaviors.receiveMessage {
              case FailureInOffsetsRetrieval(msg, exception) =>
                context.log.warn(s"Unexpected failure in offsets retrieval during query: $msg")
                exception.foreach(e => context.log.warn("Exception.", e))
                Behaviors.same
              case Stop => Behaviors.stopped { () =>
                  consumerControl.drainAndShutdown(Future.successful(()))
                  replyTo ! QueryFinished(messageCount)
                }
              case ForwardMessage(msg, to) =>
                to ! msg
                val messageOffset = msg.consumerRecord.offset()
                messageCount += 1
                if (messageOffset >= endOffsets.getOrElse(msg.consumerRecord.partition(), 0L) - 1L) {
                  ctx.log.info(
                    s"Finished query, message count: $messageCount, number of events to take: $numberOfEventsToTake"
                  )
                  context.self ! Stop
                }
                Behaviors.same
              case _: Start =>
                ctx.log.warn("Query already in progress, no need to start again.")
                Behaviors.same
            }
          } else {
            ctx.log.warn("Number of events to take is 0, so nothing to return.")
            context.self ! Stop
            Behaviors.same
          }

        }
    }
  }
}

object KafkaAsyncQueryActor {
  def apply(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    topic: String,
    replyTo: ActorRef[KafkaConsumerApi],
    endOffset: Map[Int, Long]
  )(implicit materializer: Materializer): Behavior[KafkaSubscribeActorApi] = Behaviors.setup { ctx =>
    new KafkaAsyncQueryActor(ctx, consumerSettings = consumerSettings, topic = topic, replyTo = replyTo, endOffset)
  }
}
