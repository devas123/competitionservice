package compman.compsrv.logic.actor.kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.MetadataClient
import akka.kafka.ConsumerSettings
import org.apache.kafka.common.TopicPartition

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

object OffsetsRetrievalFeature {

  def prepareOffsetsForConsumer(
    consumerSettings: ConsumerSettings[String, Array[Byte]]
  )(topic: String, startOffset: Map[Int, Long])(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem
  ): Future[Option[StartOffsetsAndTopicEndOffset]] = {
    val metadataClient = MetadataClient.create(consumerSettings, 10.seconds)
    (for {
      partitions <- metadataClient.getPartitionsFor(topic)
      topicPartitions = partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet
      partitionsToStartOffsetsMap <- metadataClient.getBeginningOffsets(topicPartitions)
      partitionsToEndOffsetsMap   <- metadataClient.getEndOffsets(topicPartitions)
      startOffsetsBoundedRight = partitionsToEndOffsetsMap
        .map(e => e._1 -> Math.min(e._2, startOffset.getOrElse(e._1.partition(), 0L)))
      startOffsetsBounded = partitionsToStartOffsetsMap
        .map(e => e._1 -> Math.max(e._2, startOffsetsBoundedRight.getOrElse(e._1, 0L)))
      res =
        if (startOffsetsBounded.nonEmpty) {
          Some(StartOffsetsAndTopicEndOffset(startOffsetsBounded, partitionsToEndOffsetsMap))
        } else None
    } yield res).andThen { case _ => Future.successful { metadataClient.close() } }
  }
}
