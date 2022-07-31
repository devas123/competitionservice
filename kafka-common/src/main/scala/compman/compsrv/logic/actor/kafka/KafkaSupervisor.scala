package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.Materializer
import akka.Done
import compman.compsrv.logic.actor.kafka.KafkaPublishActor.PublishMessageToKafka
import compman.compsrv.logic.actor.kafka.KafkaSupervisorCommand._
import org.apache.kafka.clients.admin.{Admin, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.errors.TopicExistsException

import java.util.{Properties, UUID}
import java.util
import java.util.concurrent.{ExecutionException, TimeUnit}
import scala.util.{Failure, Success, Try}

object KafkaSupervisor {
  def updated(
    publishActor: ActorRef[KafkaPublishActor.KafkaPublishActorCommand],
    queryAndSubscribeActors: Map[String, ActorRef[_]],
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    admin: Admin,
    producer: Producer[String, Array[Byte]]
  )(implicit materializer: Materializer): Behavior[KafkaSupervisorCommand] = Behaviors.setup {
    def createTopic(topic: String, topicConfig: KafkaTopicConfig) = {
      val topics = new util.ArrayList[NewTopic]()
      topics.add(new NewTopic(topic, topicConfig.numPartitions, topicConfig.replicationFactor))
      admin.createTopics(topics)
    }

    context =>
      def query(
        topic: String,
        replyTo: ActorRef[KafkaConsumerApi],
        startOffset: Map[Int, Long],
        endOffset: Map[Int, Long]
      ) = {
        val actorId = innerQueryActorId
        val actor = context.spawn(
          KafkaAsyncQueryActor(
            consumerSettings = consumerSettings.withGroupId(UUID.randomUUID().toString)
              .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"),
            topic = topic,
            replyTo = replyTo,
            endOffset
          ),
          actorId
        )

        context.spawn(
          KafkaPrepareOffsetsActor.behavior(
            consumerSettings.withGroupId(UUID.randomUUID().toString),
            actor,
            topic,
            startOffset,
            _ => Subscriptions.topics(topic)
          ),
          innerPrepareOffsetsActorId
        )

        updated(publishActor, queryAndSubscribeActors + (actorId -> actor), consumerSettings, admin, producer)
      }

      def createSubscribeActor(
        topic: String,
        consumerSettings: ConsumerSettings[String, Array[Byte]],
        replyTo: ActorRef[KafkaConsumerApi],
        uuid: String
      ) = {
        queryAndSubscribeActors.get(uuid).foreach(a => context.stop(a))
        val actorId = innerSubscribeActorId(uuid)
        val actor   = context.spawn(KafkaSubscribeActor(consumerSettings, topic, replyTo), actorId)
        context.watchWith(actor, SubscriberStopped(uuid))
        (actorId, actor)
      }

      Behaviors.receiveMessage[KafkaSupervisorCommand] {
        case Unsubscribe(uuid) =>
          queryAndSubscribeActors.get(uuid).foreach(a => context.stop(a))
          Behaviors.same
        case SubscriberStopped(uuid) =>
          updated(publishActor, queryAndSubscribeActors - uuid, consumerSettings, admin, producer)
        case Subscribe(topic, groupId, replyTo, startOffsets, uuid, commitOffsetToKafka) =>
          val (actorId, actor) = createSubscribeActor(
            topic,
            consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, commitOffsetToKafka.toString)
              .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "500").withGroupId(groupId),
            replyTo,
            uuid
          )
          val subscriptionFactory =
            (offsets: StartOffsetsAndTopicEndOffset) => Subscriptions.assignmentWithOffset(offsets.startOffsets)
          context.spawn(
            KafkaPrepareOffsetsActor.behavior(
              consumerSettings.withGroupId(UUID.randomUUID().toString),
              actor,
              topic,
              startOffsets,
              subscriptionFactory
            ),
            innerPrepareOffsetsActorId
          )
          context.log.info(
            s"Created actor with id $actorId to process query and subscribe request from topic $topic with groupId $groupId," +
              s" start offset: ${startOffsets.mkString("(", ", ", ")")}, commit offsets to Kafka: $commitOffsetToKafka."
          )
          updated(publishActor, queryAndSubscribeActors + (uuid -> actor), consumerSettings, admin, producer)

        case SubscribeToEnd(topic, groupId, replyTo, uuid, commitOffsetToKafka) =>
          val (actorId, actor) = createSubscribeActor(
            topic,
            updateConsumerSettingsWithDefaults(consumerSettings, groupId, commitOffsetToKafka),
            replyTo,
            uuid
          )
          context.log.info(
            s"Created actor with id $actorId to subscribe to the beginning of the topic $topic with groupId $groupId, commit offsets to Kafka: $commitOffsetToKafka."
          )
          startSubscriberWithAutoSubscription(topic, actor)
          updated(publishActor, queryAndSubscribeActors + (uuid -> actor), consumerSettings, admin, producer)

        case SubscribeSince(topic, groupId, replyTo, uuid, since, commitOffsetToKafka) =>
          val (actorId, actor) = createSubscribeActor(
            topic,
            updateConsumerSettingsWithDefaults(consumerSettings, groupId, commitOffsetToKafka),
            replyTo,
            uuid
          )
          val subscriptionFactory = (offsets: StartOffsetsAndTopicEndOffset) =>
            Subscriptions.assignmentOffsetsForTimes(offsets.startOffsets.map(e => e._1 -> since))
          context.spawn(
            KafkaPrepareOffsetsActor.behavior(
              consumerSettings.withGroupId(UUID.randomUUID().toString),
              actor,
              topic,
              Map.empty,
              subscriptionFactory
            ),
            innerPrepareOffsetsActorId
          )
          context.log.info(
            s"Created actor with id $actorId to subscribe to the beginning of the topic $topic with groupId $groupId, commit offsets to Kafka: $commitOffsetToKafka."
          )
          updated(publishActor, queryAndSubscribeActors + (uuid -> actor), consumerSettings, admin, producer)

        case SubscribeToBeginning(topic, groupId, replyTo, uuid, commitOffsetToKafka) =>
          val (actorId, actor) = createSubscribeActor(
            topic,
            consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, commitOffsetToKafka.toString)
              .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "500").withGroupId(groupId)
              .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
            replyTo,
            uuid
          )
          context.log.info(
            s"Created actor with id $actorId to subscribe to the end of the topic $topic with groupId $groupId, commit offsets to Kafka: $commitOffsetToKafka."
          )
          startSubscriberWithAutoSubscription(topic, actor)
          updated(publishActor, queryAndSubscribeActors + (uuid -> actor), consumerSettings, admin, producer)

        case QuerySync(topic, _, promise, timeout, startOffset, endOffset) =>
          val queryReceiver = context
            .spawn(KafkaSyncQueryReceiverActor.behavior(promise, timeout), UUID.randomUUID().toString)
          query(topic, queryReceiver, startOffset, endOffset)

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
          createTopic(topic, topicConfig)
          Behaviors.same
        case CreateTopicWithResponse(topic, topicConfig, replyTo) =>
          val result = Try { createTopic(topic, topicConfig).all().get(10, TimeUnit.SECONDS) }
            .recoverWith { case e: ExecutionException =>
              if (e.getCause.isInstanceOf[TopicExistsException]) { Success(Done) }
              else { Failure(e) }
            }.toEither.map(_ => Done)
          replyTo ! result
          Behaviors.same
      }.receiveSignal { case (_, signal) =>
        signal match {
          case _: PostStop =>
            producer.close()
            Behaviors.same
          case _ => Behaviors.same
        }
      }
  }

  private def updateConsumerSettingsWithDefaults(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    groupId: String,
    commitOffsetToKafka: Boolean
  ) = {
    consumerSettings.withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, commitOffsetToKafka.toString)
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "500").withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
  }

  private def startSubscriberWithAutoSubscription(
    topic: String,
    actor: ActorRef[KafkaSubscribeActor.KafkaSubscribeActorApi]
  ): Unit = { actor ! KafkaSubscribeActor.Start(StartOffsetsAndTopicEndOffset(), Subscriptions.topics(topic)) }

  private def stop(context: ActorContext[KafkaSupervisorCommand]) = {
    Behaviors.stopped[KafkaSupervisorCommand](() => context.log.info("Stopping kafka supervisor."))
  }

  def behavior(
    brokers: String,
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    producerSettings: ProducerSettings[String, Array[Byte]]
  ): Behavior[KafkaSupervisorCommand] = Behaviors.setup { context =>
    implicit val materializer: Materializer = Materializer.createMaterializer(context.system)
    val producer                            = producerSettings.createKafkaProducer()
    val publishActor = context
      .spawn(KafkaPublishActor.behavior(producerSettings.withProducer(producer)), UUID.randomUUID().toString)
    val properties = new Properties()
    properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    val adminClient = Admin.create(properties)
    updated(publishActor, Map.empty, consumerSettings, adminClient, producer)
  }

  private def innerQueryActorId = { s"query-${UUID.randomUUID()}" }
  private def innerSubscribeActorId(uuid: String) = { s"subscribe-$uuid" }

  private def innerPrepareOffsetsActorId = { s"prepare-offsets-actor-${UUID.randomUUID()}" }

}
