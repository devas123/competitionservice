package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, Subscription}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi.MessageReceived
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.{FailureInOffsetsRetrieval, ForwardMessage, Start, Stop}

import scala.concurrent.duration.DurationInt

private class KafkaSubscribeActor(
  context: ActorContext[KafkaSubscribeActor.KafkaSubscribeActorApi],
  consumerSettings: ConsumerSettings[String, Array[Byte]],
  topic: String,
  replyTo: ActorRef[KafkaConsumerApi]
)(implicit val materializer: Materializer)
    extends QuerySubscribeBase(context, consumerSettings) {

  context.watch(replyTo)

  context.setReceiveTimeout(5.seconds, Stop)

  override def onSignal: PartialFunction[Signal, Behavior[KafkaSubscribeActor.KafkaSubscribeActorApi]] = {
    case _: PostStop =>
      stopConsumer()
      Behaviors.same
    case Terminated(value) =>
      context.log.info(s"Reply to actor $value stopped.")
      stopConsumer()
      Behaviors.stopped
  }

  override def onMessage(
    msg: KafkaSubscribeActor.KafkaSubscribeActorApi
  ): Behavior[KafkaSubscribeActor.KafkaSubscribeActorApi] = {
    msg match {
      case FailureInOffsetsRetrieval(msg, exception) => logMessage(msg, exception, replyTo)
      case ForwardMessage(msg, to) =>
        context.cancelReceiveTimeout()
        to ! msg
        this
      case Start(_, subscription) =>
        context.cancelReceiveTimeout()
        val (consumerControl, _) = startConsumerStream(subscription, replyTo)
        this.consumerControl = Some(consumerControl)
        this
      case Stop =>
        stopConsumer()
        Behaviors.stopped

    }
  }
}

private[kafka] object KafkaSubscribeActor {

  def apply(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    topic: String,
    replyTo: ActorRef[KafkaConsumerApi]
  )(implicit materializer: Materializer): Behavior[KafkaSubscribeActorApi] = Behaviors.setup { ctx =>
    new KafkaSubscribeActor(context = ctx, consumerSettings = consumerSettings, topic = topic, replyTo = replyTo)
  }

  sealed trait KafkaSubscribeActorApi

  case object Stop                                                                       extends KafkaSubscribeActorApi
  case class FailureInOffsetsRetrieval(msg: String, exception: Option[Throwable] = None) extends KafkaSubscribeActorApi

  case class ForwardMessage(msg: MessageReceived, to: ActorRef[KafkaConsumerApi])      extends KafkaSubscribeActorApi
  case class Start(offsets: StartOffsetsAndTopicEndOffset, subscription: Subscription) extends KafkaSubscribeActorApi
}
