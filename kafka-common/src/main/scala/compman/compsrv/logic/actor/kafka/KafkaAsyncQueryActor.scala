package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.ConsumerSettings
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor._
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, QueryFinished, QueryStarted}
import org.apache.kafka.clients.consumer.ConsumerConfig

import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success}

private class KafkaAsyncQueryActor(
  context: ActorContext[KafkaSubscribeActor.KafkaQueryActorCommand],
  consumerSettings: ConsumerSettings[String, Array[Byte]],
  topic: String,
  replyTo: ActorRef[KafkaConsumerApi],
  startOffset: Option[Long],
  endOffset: Option[Long]
)(implicit val materializer: Materializer)
    extends QuerySubscribeBase(context, consumerSettings) {
  context.watchWith(replyTo, Stop)
  replyTo ! QueryStarted()
  context.pipeToSelf(prepareOffsets(topic, startOffset)) {
    case Failure(exception) => FailureInOffsetsRetrieval("Unexpected failure", Some(exception))
    case Success(value) => value match {
        case Some(offsets) =>
          val effectiveEndOffsets = offsets.endOffsets.map { case (k, v) =>
            k -> endOffset.map(eo => Math.min(eo, v)).getOrElse(v)
          }.filter(_._2 > 0)
          if (effectiveEndOffsets.nonEmpty) { Start(offsets.startOffsets, effectiveEndOffsets) }
          else {
            FailureInOffsetsRetrieval(s"No messages to retrieve from topic $topic. Start offsets: ${offsets
              .startOffsets}, provided end offset: $endOffset, calculated end offsets: $effectiveEndOffsets")
          }
        case None => Stop
      }

  }

  override def onSignal: PartialFunction[Signal, Behavior[KafkaQueryActorCommand]] = { case _: PostStop =>
    stopConsumer()
    Behaviors.same
  }

  override def onMessage(
    msg: KafkaSubscribeActor.KafkaQueryActorCommand
  ): Behavior[KafkaSubscribeActor.KafkaQueryActorCommand] = {
    msg match {
      case ForwardMessage(msg, to) =>
        context.log.warn(s"Query is not yet started, received unexpected ForwardMessage($msg, $to).")
        Behaviors.same
      case Stop                                      => Behaviors.stopped { () => replyTo ! QueryFinished(0L) }
      case FailureInOffsetsRetrieval(msg, exception) => logMessage(msg, exception, replyTo)
      case Start(off, endOffsets) => Behaviors.setup { ctx =>
          val numberOfEventsToTake = endOffsets.foldLeft(endOffsets.values.max) { case (acc, (tp, endOffset)) =>
            Math.min(acc, endOffset - off.getOrElse(tp, 0L))
          }
          ctx.log.info(
            s"Starting query with start offset $off, end offsets: $endOffsets, number of events to take: $numberOfEventsToTake"
          )
          val (consumerControl, streamFinished) = startConsumerStream(off, replyTo)
          this.consumerControl = Some(consumerControl)
          ctx.pipeToSelf(streamFinished)(_ => Stop)
          var messageCount = 0L
          Behaviors.receiveMessagePartial {
            case Stop => Behaviors.stopped { () =>
                consumerControl.drainAndShutdown(Future.successful(()))
                replyTo ! QueryFinished(messageCount)
              }
            case ForwardMessage(msg, to) =>
              to ! msg
              messageCount += 1
              if (messageCount >= numberOfEventsToTake) {
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
        }
    }
  }
}

object KafkaAsyncQueryActor {
  def apply(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    topic: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffset: Option[Long],
    endOffset: Option[Long]
  )(implicit materializer: Materializer): Behavior[KafkaQueryActorCommand] = Behaviors.setup { ctx =>
    new KafkaAsyncQueryActor(
      ctx,
      consumerSettings = consumerSettings.withGroupId(UUID.randomUUID().toString)
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"),
      topic = topic,
      replyTo = replyTo,
      startOffset = startOffset,
      endOffset = endOffset
    )
  }
}
