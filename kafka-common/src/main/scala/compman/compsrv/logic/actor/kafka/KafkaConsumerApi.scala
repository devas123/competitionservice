package compman.compsrv.logic.actor.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

sealed trait KafkaConsumerApi

object KafkaConsumerApi {
  final case class QueryStarted() extends KafkaConsumerApi

  final case class QueryFinished(count: Long) extends KafkaConsumerApi

  final case class QueryError(error: Throwable) extends KafkaConsumerApi

  final case class MessageReceived(topic: String, consumerRecord: ConsumerRecord[String, Array[Byte]])
      extends KafkaConsumerApi
}
