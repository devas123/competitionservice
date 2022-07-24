package compman.compsrv.logic.actor.kafka

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.ForwardMessage
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, MessageReceived, QueryFinished}
import org.apache.kafka.common.TopicPartition

import scala.concurrent.{ExecutionContextExecutor, Future}

abstract class QuerySubscribeBase(
  context: ActorContext[KafkaSubscribeActor.KafkaQueryActorCommand],
  consumerSettings: ConsumerSettings[String, Array[Byte]]
) extends AbstractBehavior[KafkaSubscribeActor.KafkaQueryActorCommand](context) with OffsetsRetrievalFeature {
  protected implicit val dispatcher: ExecutionContextExecutor = context.executionContext
  protected implicit val classicActorSystem: ActorSystem      = context.system.classicSystem
  protected var consumerControl: Option[Consumer.Control]     = None
  def prepareOffsets(topic: String, startOffset: Option[Long]): Future[Option[StartOffsetsAndTopicEndOffset]] =
    prepareOffsetsForConsumer(consumerSettings)(topic, startOffset)
  protected def stopConsumer(): Unit = { consumerControl.foreach(cc => cc.shutdown()) }

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
