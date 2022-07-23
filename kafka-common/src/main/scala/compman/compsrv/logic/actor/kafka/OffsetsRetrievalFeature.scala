package compman.compsrv.logic.actor.kafka

import akka.actor.ActorRef
import akka.kafka.scaladsl.MetadataClient
import org.apache.kafka.common.TopicPartition

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

trait OffsetsRetrievalFeature {

  def prepareOffsetsForConsumer(consumer: ActorRef)(topic: String, startOffset: Option[Long])(implicit
    ec: ExecutionContext
  ): Future[Option[StartOffsetsAndTopicEndOffset]] = {
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
}
