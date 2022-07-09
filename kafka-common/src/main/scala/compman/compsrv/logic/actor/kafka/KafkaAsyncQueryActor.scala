package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.{ForwardMesage, KafkaQueryActorCommand, Start, Stop}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, MessageReceived}
import org.apache.kafka.clients.consumer.ConsumerConfig

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
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
  context.pipeToSelf(prepareOffsets(topic, startOffset)) {
    case Failure(exception) =>
      context.log.error(s"Error while getting the offsets for topic $topic", exception)
      Stop
    case Success(value) => value match {
        case Some(offsets) => Start(offsets, endOffset)
        case None =>
          context.log.error(s"No offsets for $topic")
          Stop
      }

  }

  override def onMessage(
    msg: KafkaSubscribeActor.KafkaQueryActorCommand
  ): Behavior[KafkaSubscribeActor.KafkaQueryActorCommand] = {
    msg match {
      case ForwardMesage(msg, to) =>
        to ! msg
        Behaviors.same
      case Stop => Behaviors.stopped(() => ())
      case Start(off, endOffset) => Behaviors.setup { ctx =>
          val (consumerControl, _) = Consumer.plainSource(consumerSettings, Subscriptions.assignmentWithOffset(off))
            .takeWhile(rec => endOffset.exists(e => e >= rec.offset()))
            .map(e => replyTo ! MessageReceived(topic = topic, consumerRecord = e)).toMat(Sink.ignore)(Keep.both).run()
          Behaviors.receiveMessage {
            case KafkaSubscribeActor.Stop => Behaviors.stopped(() =>
              Await.result(consumerControl.shutdown().map(_ => ()), 10.seconds)
            )
            case ForwardMesage(msg, to) =>
              to ! msg
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
