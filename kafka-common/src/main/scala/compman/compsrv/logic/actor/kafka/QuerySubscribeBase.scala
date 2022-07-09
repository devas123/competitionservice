package compman.compsrv.logic.actor.kafka

import akka.actor
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import akka.kafka.{ConsumerSettings, KafkaConsumerActor}
import akka.kafka.scaladsl.MetadataClient
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

  def prepareOffsets(topic: String, startOffset: Option[Long]): Future[Option[Map[TopicPartition, Long]]] = {
    val metadataClient = MetadataClient.create(consumer, 1.second)
    for {
      partitions <- metadataClient.getPartitionsFor(topic)
      topicPartitions = partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet
      kafkaTopicEndOffsetsMap <- metadataClient.getEndOffsets(topicPartitions)
      partitionsToEndOffsetsMap = kafkaTopicEndOffsetsMap
      filteredOffsets           = partitionsToEndOffsetsMap.filter(_._2 > startOffset.getOrElse(0L))
      res =
        if (filteredOffsets.nonEmpty) {
          val off = filteredOffsets.keySet.map(tp => (tp, partitionsToEndOffsetsMap(tp))).filter(o => o._2 > 0).toMap
          Some(off)
        } else None
    } yield res
  }
}
