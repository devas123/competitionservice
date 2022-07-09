package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.{ForwardMesage, Start, Stop}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

private class KafkaSubscribeActor(
  context: ActorContext[KafkaSubscribeActor.KafkaQueryActorCommand],
  consumerSettings: ConsumerSettings[String, Array[Byte]],
  topic: String,
  startOffset: Option[Long],
  replyTo: ActorRef[KafkaConsumerApi]
)(implicit val materializer: Materializer)
    extends QuerySubscribeBase(context, consumerSettings) {

  context.pipeToSelf(prepareOffsets(topic, startOffset)) {
    case Failure(exception) =>
      context.log.error("Error while preparing offsets", exception)
      Stop
    case Success(value) => value match {
        case Some(value) => Start(value, None)
        case None        => Stop
      }
  }

  override def onMessage(
    msg: KafkaSubscribeActor.KafkaQueryActorCommand
  ): Behavior[KafkaSubscribeActor.KafkaQueryActorCommand] = {
    msg match {
      case ForwardMesage(msg, to) =>
        to ! msg
        this
      case Stop => Behaviors.stopped(() => ())
      case Start(off, _) => Behaviors.setup { ctx =>
          val (consumerControl, _) = Consumer.plainSource(consumerSettings, Subscriptions.assignmentWithOffset(off))
            .map(e => replyTo ! MessageReceived(topic = topic, consumerRecord = e)).toMat(Sink.ignore)(Keep.both).run()
          Behaviors.receiveMessage[KafkaSubscribeActor.KafkaQueryActorCommand] {
            case ForwardMesage(msg, to) =>
              to ! msg
              Behaviors.same
            case Stop => Behaviors.stopped(() => Await.result(consumerControl.shutdown().map(_ => ()), 10.seconds))
            case _: Start =>
              ctx.log.error("Stream already started.")
              Behaviors.same
          }
        }
    }
  }
}

private[kafka] object KafkaSubscribeActor {

  def apply(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffset: Option[Long],
    commitOffsetsToKafka: Boolean
  )(implicit materializer: Materializer): Behavior[KafkaQueryActorCommand] = Behaviors.setup { ctx =>
    val startFixed = startOffset.map(so => java.lang.Long.max(so, 0L).longValue())
    new KafkaSubscribeActor(
      context = ctx,
      consumerSettings = consumerSettings
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, commitOffsetsToKafka.toString)
        .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "500").withGroupId(groupId),
      topic = topic,
      startOffset = startFixed,
      replyTo = replyTo
    )
  }

  sealed trait KafkaQueryActorCommand

  case object Stop extends KafkaQueryActorCommand

  case class ForwardMesage(msg: MessageReceived, to: ActorRef[KafkaConsumerApi]) extends KafkaQueryActorCommand
  case class Start(off: Map[TopicPartition, Long], endOffset: Option[Long])      extends KafkaQueryActorCommand
}
