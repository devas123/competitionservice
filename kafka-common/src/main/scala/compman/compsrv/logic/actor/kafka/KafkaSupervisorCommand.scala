package compman.compsrv.logic.actor.kafka

import akka.actor.typed.ActorRef
import akka.Done

import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.Promise

sealed trait KafkaSupervisorCommand

object KafkaSupervisorCommand {
  final case class KafkaTopicConfig(
    numPartitions: Int = 1,
    replicationFactor: Short = 1,
    additionalProperties: Map[String, String] = Map.empty
  )
  case class Subscribe(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffsets: Map[Int, Long],
    uuid: String = UUID.randomUUID().toString,
    commitOffsetToKafka: Boolean = false
  ) extends KafkaSupervisorCommand

  case class SubscribeToBeginning(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    uuid: String = UUID.randomUUID().toString,
    commitOffsetToKafka: Boolean = false
  ) extends KafkaSupervisorCommand

  case class SubscribeToEnd(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    uuid: String = UUID.randomUUID().toString,
    commitOffsetToKafka: Boolean = false
  ) extends KafkaSupervisorCommand

  case class SubscribeSince(
                             topic: String,
                             groupId: String,
                             replyTo: ActorRef[KafkaConsumerApi],
                             uuid: String = UUID.randomUUID().toString,
                             since: Long,
                             commitOffsetToKafka: Boolean = false
                           ) extends KafkaSupervisorCommand

  case class CreateTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig) extends KafkaSupervisorCommand
  case class CreateTopicWithResponse(topic: String, topicConfig: KafkaTopicConfig, replyTo: ActorRef[Either[Throwable, Done]]) extends KafkaSupervisorCommand

  case class QueryAsync(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffset: Map[Int, Long] = Map.empty,
    endOffset: Map[Int, Long] = Map.empty
  ) extends KafkaSupervisorCommand


  private[kafka] case class SubscriberStopped(uuid: String) extends KafkaSupervisorCommand

  case class QuerySync(
    topic: String,
    groupId: String,
    promise: Promise[Seq[Array[Byte]]],
    timeout: FiniteDuration = 10.seconds,
    startOffset: Map[Int, Long] = Map.empty,
    endOffset: Map[Int, Long] = Map.empty
  ) extends KafkaSupervisorCommand

  case class Unsubscribe(uuid: String) extends KafkaSupervisorCommand

  case class PublishMessage(topic: String, key: String, message: Array[Byte]) extends KafkaSupervisorCommand

  case class BatchPublishMessage(messages: Seq[PublishMessage]) extends KafkaSupervisorCommand
  case object Stop                                              extends KafkaSupervisorCommand

  object PublishMessage {
    def apply(tuple: (String, String, Array[Byte])): PublishMessage = PublishMessage(tuple._1, tuple._2, tuple._3)
  }

}
