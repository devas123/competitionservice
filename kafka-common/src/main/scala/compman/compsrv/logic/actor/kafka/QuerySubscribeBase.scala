package compman.compsrv.logic.actor.kafka

import akka.{actor, Done}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.kafka.{ConsumerSettings, KafkaConsumerActor, Subscriptions}
import akka.kafka.scaladsl.{Consumer, MetadataClient}
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaSubscribeActor.ForwardMessage
import compman.compsrv.logic.actor.kafka.KafkaSupervisor.{KafkaConsumerApi, MessageReceived, QueryFinished}
import org.apache.kafka.common.TopicPartition

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt

abstract class QuerySubscribeBase(
  context: ActorContext[KafkaSubscribeActor.KafkaQueryActorCommand],
  consumerSettings: ConsumerSettings[String, Array[Byte]]
) extends AbstractBehavior[KafkaSubscribeActor.KafkaQueryActorCommand](context) {
  import akka.actor.typed.scaladsl.adapter._
  protected implicit val dispatcher: ExecutionContextExecutor = context.executionContext
  protected val consumer: actor.ActorRef = context
    .actorOf(KafkaConsumerActor.props(consumerSettings), "kafka-consumer-actor")

  case class StartOffsetsAndTopicEndOffset(
    startOffsets: Map[TopicPartition, Long],
    endOffsets: Map[TopicPartition, Long]
  )

  def prepareOffsets(topic: String, startOffset: Option[Long]): Future[Option[StartOffsetsAndTopicEndOffset]] = {
    val metadataClient = MetadataClient.create(consumer, 30.second)
    for {
      partitions <- metadataClient.getPartitionsFor(topic)
      topicPartitions = partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet
      partitionsToEndOffsetsMap <- metadataClient.getEndOffsets(topicPartitions)
      startOffsets = partitionsToEndOffsetsMap.map(e => e._1 -> startOffset.map(o => Math.min(o, e._2)).getOrElse(e._2))
      res =
        if (startOffsets.nonEmpty) { Some(StartOffsetsAndTopicEndOffset(startOffsets, partitionsToEndOffsetsMap)) }
        else None
    } yield res
  }

  protected def logMessage(msg: String, exception: Option[Throwable], replyTo: ActorRef[KafkaConsumerApi]): Behavior[KafkaSubscribeActor.KafkaQueryActorCommand] = {
    exception match {
      case Some(value) => context.log.error(msg, value)
      case None        => context.log.error(msg)
    }
    Behaviors.stopped[KafkaSubscribeActor.KafkaQueryActorCommand] { () =>
      replyTo ! QueryFinished(0L)
    }
  }

  protected def startConsumerStream(startOffsets: Map[TopicPartition, Long], replyTo: ActorRef[KafkaConsumerApi])(implicit mat: Materializer): (Consumer.Control, Future[Done]) = {
    Consumer
      .plainSource(consumerSettings, Subscriptions.assignmentWithOffset(startOffsets))
      .map(e => context.self ! ForwardMessage(MessageReceived(topic = e.topic(), consumerRecord = e), replyTo)).toMat(Sink.ignore)(Keep.both).async
      .run()
  }

}
