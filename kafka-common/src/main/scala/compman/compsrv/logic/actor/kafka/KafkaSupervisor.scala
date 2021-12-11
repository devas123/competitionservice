package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaPublishActor.PublishMessageToKafka
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import zio.{Fiber, Promise, RIO, Tag, Task}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.{durationInt, Duration}
import zio.kafka.admin.{AdminClient, AdminClientSettings}
import zio.kafka.consumer.CommittableRecord
import zio.logging.Logging

import java.util.UUID

object KafkaSupervisor {

  final case class KafkaTopicConfig(
    numPartitions: Int = 1,
    replicationFactor: Short = 1,
    additionalProperties: Map[String, String] = Map.empty
  )

  sealed trait KafkaConsumerApi

  final case class QueryStarted() extends KafkaConsumerApi

  final case class QueryFinished() extends KafkaConsumerApi

  final case class QueryError(error: Throwable) extends KafkaConsumerApi

  final case class MessageReceived(topic: String, committableRecord: CommittableRecord[String, Array[Byte]])
      extends KafkaConsumerApi

  sealed trait KafkaSupervisorCommand

  case class QueryAndSubscribe(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi])
      extends KafkaSupervisorCommand

  case class CreateTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig) extends KafkaSupervisorCommand

  case class QueryAsync(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi])
      extends KafkaSupervisorCommand

  case class QuerySync(
    topic: String,
    groupId: String,
    promise: Promise[Throwable, Seq[Array[Byte]]],
    timeout: Duration = 10.seconds
  ) extends KafkaSupervisorCommand

  case class Subscribe(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi])
      extends KafkaSupervisorCommand

  case class PublishMessage(topic: String, key: String, message: Array[Byte]) extends KafkaSupervisorCommand

  case object Stop extends KafkaSupervisorCommand

  type KafkaSupervisorEnvironment[R] = R with Logging with Clock with Blocking

  def behavior[R: Tag](brokers: List[String]): ActorBehavior[KafkaSupervisorEnvironment[R], Option[
    ActorRef[KafkaPublishActor.KafkaPublishActorCommand]
  ], KafkaSupervisorCommand] = new ActorBehavior[KafkaSupervisorEnvironment[R], Option[
    ActorRef[KafkaPublishActor.KafkaPublishActorCommand]
  ], KafkaSupervisorCommand] {
    override def receive(
                          context: Context[KafkaSupervisorCommand],
                          actorConfig: ActorConfig,
                          state: Option[ActorRef[KafkaPublishActor.KafkaPublishActorCommand]],
                          command: KafkaSupervisorCommand,
                          timers: Timers[KafkaSupervisorEnvironment[R], KafkaSupervisorCommand]
    ): RIO[KafkaSupervisorEnvironment[R], Option[ActorRef[KafkaPublishActor.KafkaPublishActorCommand]]] =
      command match {
        case QueryAndSubscribe(topic, groupId, replyTo) => context.make(
            UUID.randomUUID().toString,
            ActorConfig(),
            (),
            KafkaQueryAndSubscribeActor.behavior(topic, groupId, replyTo, brokers, subscribe = true, query = true)
          ).as(state)
        case QuerySync(topic, groupId, promise, timeout) => for {
            queryReceiver <- context.make(
              UUID.randomUUID().toString,
              ActorConfig(),
              Seq.empty[Array[Byte]],
              KafkaSyncQueryReceiverActor.behavior(promise, timeout)
            )
            _ <- context.make(
              UUID.randomUUID().toString,
              ActorConfig(),
              (),
              KafkaQueryAndSubscribeActor
                .behavior(topic, groupId, queryReceiver, brokers, subscribe = false, query = true)
            )
          } yield state

        case QueryAsync(topic, groupId, replyTo) => context.make(
            UUID.randomUUID().toString,
            ActorConfig(),
            (),
            KafkaQueryAndSubscribeActor.behavior(topic, groupId, replyTo, brokers, subscribe = false, query = true)
          ).as(state)
        case Subscribe(topic, groupId, replyTo) => context.make(
            UUID.randomUUID().toString,
            ActorConfig(),
            (),
            KafkaQueryAndSubscribeActor.behavior(topic, groupId, replyTo, brokers, subscribe = true, query = false)
          ).as(state)
        case Stop => context.stopSelf.as(state)
        case PublishMessage(topic, key, message) => state.fold(Task(()))(_ ! PublishMessageToKafka(topic, key, message))
            .as(state)
        case CreateTopicIfMissing(topic, topicConfig) => AdminClient.make(AdminClientSettings(brokers))
            .use(_.createTopic(AdminClient.NewTopic(topic, topicConfig.numPartitions, topicConfig.replicationFactor)))
            .as(state)

      }

    override def init(
                       actorConfig: ActorConfig,
                       context: Context[KafkaSupervisorCommand],
                       initState: Option[ActorRef[KafkaPublishActor.KafkaPublishActorCommand]],
                       timers: Timers[KafkaSupervisorEnvironment[R], KafkaSupervisorCommand]
    ): RIO[KafkaSupervisorEnvironment[
      R
    ], (Seq[Fiber[Throwable, Unit]], Seq[KafkaSupervisorCommand], Option[ActorRef[KafkaPublishActor.KafkaPublishActorCommand]])] =
      for {
        publishActor <- initState.map(RIO(_))
          .getOrElse(context.make("KafkaPublishActor", ActorConfig(), (), KafkaPublishActor.behavior[R](brokers)))
      } yield (Seq.empty, Seq.empty, Some(publishActor))
  }
}
