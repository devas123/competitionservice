package compman.compsrv.logic.actor.kafka

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, Subscription}
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaConsumerApi.{MessageReceived, QueryFinished}
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.ForwardMessage

import scala.concurrent.Future

abstract class QuerySubscribeBase(
  context: ActorContext[KafkaSubscribeActor.KafkaSubscribeActorApi],
  consumerSettings: ConsumerSettings[String, Array[Byte]]
) extends AbstractBehavior[KafkaSubscribeActor.KafkaSubscribeActorApi](context) {
  protected var consumerControl: Option[Consumer.Control] = None
  protected def stopConsumer(): Unit = { consumerControl.foreach(cc => cc.shutdown()) }

  protected def logMessage(
    msg: String,
    exception: Option[Throwable],
    replyTo: ActorRef[KafkaConsumerApi]
  ): Behavior[KafkaSubscribeActor.KafkaSubscribeActorApi] = {
    exception match {
      case Some(value) => context.log.error(msg, value)
      case None        => context.log.error(msg)
    }
    replyTo ! QueryFinished(0L)
    Behaviors.stopped[KafkaSubscribeActor.KafkaSubscribeActorApi]
  }

  protected def startConsumerStream(subscription: Subscription, replyTo: ActorRef[KafkaConsumerApi])(implicit
    mat: Materializer
  ): (Consumer.Control, Future[Done]) = {
    Consumer.plainSource(consumerSettings, subscription)
      .map(e => context.self ! ForwardMessage(MessageReceived(topic = e.topic(), consumerRecord = e), replyTo))
      .toMat(Sink.ignore)(Keep.both).run()
  }

}
