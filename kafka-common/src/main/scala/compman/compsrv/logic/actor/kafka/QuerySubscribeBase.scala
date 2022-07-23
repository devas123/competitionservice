package compman.compsrv.logic.actor.kafka

import akka.{actor, Done}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, KafkaConsumerActor, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.ForwardMessage
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, MessageReceived, QueryFinished}
import org.apache.kafka.common.TopicPartition

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

abstract class QuerySubscribeBase(
  context: ActorContext[KafkaSubscribeActor.KafkaQueryActorCommand],
  consumerSettings: ConsumerSettings[String, Array[Byte]]
) extends AbstractBehavior[KafkaSubscribeActor.KafkaQueryActorCommand](context) with OffsetsRetrievalFeature {
  import akka.actor.typed.scaladsl.adapter._
  protected implicit val dispatcher: ExecutionContextExecutor = context.executionContext
  protected val consumer: actor.ActorRef = context
    .actorOf(KafkaConsumerActor.props(consumerSettings), s"kafka-consumer-actor-${UUID.randomUUID()}")

  def prepareOffsets(topic: String, startOffset: Option[Long]): Future[Option[StartOffsetsAndTopicEndOffset]] =
    prepareOffsetsForConsumer(consumer)(topic, startOffset)

  protected def logMessage(
    msg: String,
    exception: Option[Throwable],
    replyTo: ActorRef[KafkaConsumerApi]
  ): Behavior[KafkaSubscribeActor.KafkaQueryActorCommand] = {
    exception match {
      case Some(value) => context.log.error(msg, value)
      case None        => context.log.error(msg)
    }
    Behaviors.stopped[KafkaSubscribeActor.KafkaQueryActorCommand] { () => replyTo ! QueryFinished(0L) }
  }

  protected def startConsumerStream(startOffsets: Map[TopicPartition, Long], replyTo: ActorRef[KafkaConsumerApi])(
    implicit mat: Materializer
  ): (Consumer.Control, Future[Done]) = {
    Consumer.plainSource(consumerSettings, Subscriptions.assignmentWithOffset(startOffsets))
      .map(e => context.self ! ForwardMessage(MessageReceived(topic = e.topic(), consumerRecord = e), replyTo))
      .toMat(Sink.ignore)(Keep.both).async.run()
  }

}
