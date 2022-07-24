package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.ActorSystem
import akka.kafka.ConsumerSettings
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaOffsetsQueryActor.{ErrorDuringQuery, KafkaOffsetsQueryActorApi, ReceivedOffsets}
import compman.compsrv.logic.actor.kafka.KafkaSyncOffsetQueryReceiverActor.{ErrorDuringMetadataRequest, OffsetsReceived}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

private class KafkaOffsetsQueryActor(
  context: ActorContext[KafkaOffsetsQueryActorApi],
  consumerSettings: ConsumerSettings[String, Array[Byte]],
  topic: String,
  replyTo: ActorRef[KafkaSyncOffsetQueryReceiverActor.KafkaSyncOffsetQueryReceiverActorApi]
)(implicit val materializer: Materializer)
    extends AbstractBehavior[KafkaOffsetsQueryActorApi](context) with OffsetsRetrievalFeature {
  protected implicit val dispatcher: ExecutionContextExecutor = context.executionContext
  protected implicit val classicActorSystem: ActorSystem      = context.system.classicSystem

  context.pipeToSelf(prepareOffsetsForConsumer(consumerSettings)(topic, None)) {
    case Failure(exception) => ErrorDuringQuery(exception)
    case Success(value) => value match {
        case Some(offsets) => ReceivedOffsets(offsets)
        case None          => ErrorDuringQuery(new Exception("No offsets received"))
      }

  }

  override def onMessage(msg: KafkaOffsetsQueryActorApi): Behavior[KafkaOffsetsQueryActorApi] = msg match {
    case ReceivedOffsets(offsets) =>
      replyTo ! OffsetsReceived(offsets)
      Behaviors.stopped(() => ())
    case ErrorDuringQuery(error) =>
      replyTo ! ErrorDuringMetadataRequest(error)
      Behaviors.stopped(() => ())
  }
}

object KafkaOffsetsQueryActor {
  def apply(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    topic: String,
    replyTo: ActorRef[KafkaSyncOffsetQueryReceiverActor.KafkaSyncOffsetQueryReceiverActorApi]
  )(implicit mat: Materializer): Behavior[KafkaOffsetsQueryActorApi] = Behaviors.setup { ctx =>
    new KafkaOffsetsQueryActor(context = ctx, consumerSettings = consumerSettings, topic = topic, replyTo = replyTo)
  }
  sealed trait KafkaOffsetsQueryActorApi
  case class ReceivedOffsets(offsets: StartOffsetsAndTopicEndOffset) extends KafkaOffsetsQueryActorApi
  case class ErrorDuringQuery(error: Throwable)                      extends KafkaOffsetsQueryActorApi
}
