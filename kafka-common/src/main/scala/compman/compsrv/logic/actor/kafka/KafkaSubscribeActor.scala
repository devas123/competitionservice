package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.ConsumerSettings
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.{FailureInOffsetsRetrieval, ForwardMessage, Start, Stop}
import compman.compsrv.logic.actor.kafka.KafkaSupervisor._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition

import scala.concurrent.Future
import scala.util.{Failure, Success}

private class KafkaSubscribeActor(
  context: ActorContext[KafkaSubscribeActor.KafkaQueryActorCommand],
  consumerSettings: ConsumerSettings[String, Array[Byte]],
  topic: String,
  startOffset: Option[Long],
  replyTo: ActorRef[KafkaConsumerApi]
)(implicit val materializer: Materializer)
    extends QuerySubscribeBase(context, consumerSettings) {

  context.watchWith(replyTo, Stop)

  context.pipeToSelf(prepareOffsets(topic, startOffset)) {
    case Failure(exception) => FailureInOffsetsRetrieval("Error while preparing offsets", Some(exception))
    case Success(value) => value match {
        case Some(value) => Start(value.startOffsets, value.endOffsets)
        case None        => FailureInOffsetsRetrieval("No offsets were retrieved")
      }
  }

  override def onSignal: PartialFunction[Signal, Behavior[KafkaSubscribeActor.KafkaQueryActorCommand]] = {
    case _: PostStop =>
      stopConsumer()
      Behaviors.same
  }

  override def onMessage(
    msg: KafkaSubscribeActor.KafkaQueryActorCommand
  ): Behavior[KafkaSubscribeActor.KafkaQueryActorCommand] = {
    msg match {
      case FailureInOffsetsRetrieval(msg, exception) => logMessage(msg, exception, replyTo)
      case ForwardMessage(msg, to) =>
        to ! msg
        this
      case Stop => Behaviors.stopped
      case Start(off, _) => Behaviors.setup { ctx =>
          val (consumerControl, _) = startConsumerStream(off, replyTo)
          this.consumerControl = Some(consumerControl)
          Behaviors.receiveMessagePartial {
            case ForwardMessage(msg, to) =>
              to ! msg
              Behaviors.same
            case Stop =>
              consumerControl.drainAndShutdown(Future.successful(()))
              Behaviors.stopped
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

  case object Stop                                                                       extends KafkaQueryActorCommand
  case class FailureInOffsetsRetrieval(msg: String, exception: Option[Throwable] = None) extends KafkaQueryActorCommand

  case class ForwardMessage(msg: MessageReceived, to: ActorRef[KafkaConsumerApi])         extends KafkaQueryActorCommand
  case class Start(off: Map[TopicPartition, Long], endOffsets: Map[TopicPartition, Long]) extends KafkaQueryActorCommand
}
