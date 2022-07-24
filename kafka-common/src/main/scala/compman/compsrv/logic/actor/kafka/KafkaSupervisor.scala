package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.stream.Materializer
import compman.compsrv.logic.actor.kafka.KafkaPublishActor.PublishMessageToKafka
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.consumer.ConsumerRecord

import java.util.{Properties, UUID}
import scala.concurrent.Promise
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object KafkaSupervisor {

  final case class KafkaTopicConfig(
    numPartitions: Int = 1,
    replicationFactor: Short = 1,
    additionalProperties: Map[String, String] = Map.empty
  )

  sealed trait KafkaConsumerApi

  final case class QueryStarted() extends KafkaConsumerApi

  final case class QueryFinished(count: Long) extends KafkaConsumerApi

  final case class QueryError(error: Throwable) extends KafkaConsumerApi

  final case class MessageReceived(topic: String, consumerRecord: ConsumerRecord[String, Array[Byte]])
      extends KafkaConsumerApi

  sealed trait KafkaSupervisorCommand

  case class QueryAndSubscribe(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffset: Option[Long] = None,
    uuid: String = UUID.randomUUID().toString,
    commitOffsetToKafka: Boolean = false
  ) extends KafkaSupervisorCommand

  case class CreateTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig) extends KafkaSupervisorCommand

  case class QueryAsync(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffset: Option[Long] = Some(0),
    endOffset: Option[Long] = None
  ) extends KafkaSupervisorCommand

  case class QueryOffsetsSync(
                               topic: String,
                               groupId: String,
                               promise: Promise[StartOffsetsAndTopicEndOffset],
                               timeout: FiniteDuration = 10.seconds
  ) extends KafkaSupervisorCommand

  private case class SubscriberStopped(uuid: String) extends KafkaSupervisorCommand

  case class QuerySync(
    topic: String,
    groupId: String,
    promise: Promise[Seq[Array[Byte]]],
    timeout: FiniteDuration = 10.seconds,
    startOffset: Option[Long] = Some(0),
    endOffset: Option[Long] = None
  ) extends KafkaSupervisorCommand

  case class Unsubscribe(uuid: String) extends KafkaSupervisorCommand

  case class PublishMessage(topic: String, key: String, message: Array[Byte]) extends KafkaSupervisorCommand
  case class BatchPublishMessage(messages: Seq[PublishMessage])               extends KafkaSupervisorCommand
  object PublishMessage {
    def apply(tuple: (String, String, Array[Byte])): PublishMessage = PublishMessage(tuple._1, tuple._2, tuple._3)
  }

  case object Stop extends KafkaSupervisorCommand

  final case class KafkaSupervisorState(
    publishActor: Option[ActorRef[KafkaPublishActor.KafkaPublishActorCommand]],
    queryAndSubscribeActors: Map[String, ActorRef[KafkaSubscribeActor.KafkaQueryActorCommand]]
  )


  def updated(
    publishActor: ActorRef[KafkaPublishActor.KafkaPublishActorCommand],
    queryAndSubscribeActors: Map[String, ActorRef[_]],
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    admin: Admin
  )(implicit materializer: Materializer): Behavior[KafkaSupervisorCommand] = Behaviors.setup { context =>
    def query(
      topic: String,
      replyTo: ActorRef[KafkaConsumerApi],
      startOffset: Option[Long],
      endOffset: Option[Long]
    ) = {
      val actorId = innerQueryActorId
      val actor = context.spawn(
        KafkaAsyncQueryActor(
          consumerSettings = consumerSettings,
          topic = topic,
          replyTo = replyTo,
          startOffset = startOffset,
          endOffset = endOffset
        ),
        actorId
      )
      updated(publishActor, queryAndSubscribeActors + (actorId -> actor), consumerSettings, admin)
    }

    def queryOffsetsSync(
               topic: String,
               groupId: String,
               replyTo: ActorRef[KafkaSyncOffsetQueryReceiverActor.KafkaSyncOffsetQueryReceiverActorApi],
             ) = {
      val actorId = innerQueryActorId
      val actor = context.spawn(
        KafkaOffsetsQueryActor(
          consumerSettings = consumerSettings.withGroupId(groupId),
          topic = topic,
          replyTo = replyTo,
        ),
        actorId
      )
      updated(publishActor, queryAndSubscribeActors + (actorId -> actor), consumerSettings, admin)
    }


    Behaviors.receiveMessage {
      case Unsubscribe(uuid) =>
        queryAndSubscribeActors.get(uuid).foreach(a => context.stop(a))
        Behaviors.same
      case SubscriberStopped(uuid) => updated(publishActor, queryAndSubscribeActors - uuid, consumerSettings, admin)
      case QueryAndSubscribe(topic, groupId, replyTo, startOffset, uuid, commitOffsetToKafka) =>
        queryAndSubscribeActors.get(uuid).foreach(a => context.stop(a))
        val actorId = innerQueryActorId
        val actor = context.spawn(
          KafkaSubscribeActor(
            consumerSettings,
            topic,
            groupId,
            replyTo,
            startOffset = startOffset,
            commitOffsetsToKafka = commitOffsetToKafka
          ),
          actorId
        )
        context.watchWith(actor, SubscriberStopped(uuid))
        context.log.info(s"Created actor with id $actorId to process query and subscribe request.")
        updated(publishActor, queryAndSubscribeActors + (actorId -> actor), consumerSettings, admin)

      case QuerySync(topic, _, promise, timeout, startOffset, endOffset) =>
        val queryReceiver = context
          .spawn(KafkaSyncQueryReceiverActor.behavior(promise, timeout), UUID.randomUUID().toString)
        query(topic, queryReceiver, startOffset, endOffset)
      case QueryOffsetsSync(topic, groupId, promise, timeout) =>
        val queryReceiver = context
          .spawn(KafkaSyncOffsetQueryReceiverActor.behavior(promise, timeout), UUID.randomUUID().toString)
        queryOffsetsSync(topic, groupId, queryReceiver)

      case QueryAsync(topic, _, replyTo, startOffset, endOffset) => query(topic, replyTo, startOffset, endOffset)
      case Stop                                                  => stop(context)
      case PublishMessage(topic, key, message) =>
        publishActor ! PublishMessageToKafka(topic, key, message)
        Behaviors.same
      case BatchPublishMessage(messages) =>
        messages.foreach { case PublishMessage(topic, key, message) =>
          publishActor ! PublishMessageToKafka(topic, key, message)
        }
        Behaviors.same

      case CreateTopicIfMissing(topic, topicConfig) =>
        val topics = new java.util.ArrayList[NewTopic]()
        topics.add(new NewTopic(topic, topicConfig.numPartitions, topicConfig.replicationFactor))
        admin.createTopics(topics)
        Behaviors.same
    }
  }

  private def stop(context: ActorContext[KafkaSupervisorCommand]) = {
    Behaviors.stopped[KafkaSupervisorCommand](() => context.log.info("Stopping kafka supervisor."))
  }

  def behavior(
    brokers: String,
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    producerSettings: ProducerSettings[String, Array[Byte]]
  ): Behavior[KafkaSupervisorCommand] = Behaviors.setup { context =>
    implicit val materializer: Materializer = Materializer.createMaterializer(context.system)
    val publishActor = context.spawn(KafkaPublishActor.behavior(producerSettings), UUID.randomUUID().toString)
    val properties   = new Properties()
    properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    val adminClient = Admin.create(properties)
    updated(publishActor, Map.empty, consumerSettings, adminClient)
  }

  private def innerQueryActorId = { s"queryAndSubscribe-${UUID.randomUUID()}" }
}
