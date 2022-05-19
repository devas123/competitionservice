package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaPublishActor.PublishMessageToKafka
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Behaviors}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import org.apache.kafka.common.errors.TopicExistsException
import zio.{Promise, RIO, Tag, Task}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
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

  case class PublishMessage(topic: String, key: String, message: Array[Byte]) extends KafkaSupervisorCommand {
  }
  object PublishMessage {
    def apply(tuple: (String, String, Array[Byte])): PublishMessage = PublishMessage(tuple._1, tuple._2, tuple._3)
  }

  case object Stop extends KafkaSupervisorCommand

  type KafkaSupervisorEnvironment[R] = R with Logging with Clock with Blocking with Console

  import Behaviors._
  def behavior[R: Tag](brokers: List[String]): ActorBehavior[KafkaSupervisorEnvironment[R], Option[
    ActorRef[KafkaPublishActor.KafkaPublishActorCommand]
  ], KafkaSupervisorCommand] = Behaviors
    .behavior[KafkaSupervisorEnvironment[R], Option[
      ActorRef[KafkaPublishActor.KafkaPublishActorCommand]
    ], KafkaSupervisorCommand].withReceive { (context, _, state, command, _) =>
      command match {
        case QueryAndSubscribe(topic, groupId, replyTo) => for {
            actorId <- getQueryActorId
            state <- KafkaQueryAndSubscribeActor(actorId, context)(
              topic,
              groupId,
              replyTo,
              brokers,
              subscribe = true,
              query = true
            ).as(state)
            _ <- Logging.info(s"Created actor with id $actorId to process query and subscribe request.")
          } yield state
        case QuerySync(topic, groupId, promise, timeout) => for {
            actorId <- getQueryActorId
            queryReceiver <- context.make(
              UUID.randomUUID().toString,
              ActorConfig(),
              Seq.empty[Array[Byte]],
              KafkaSyncQueryReceiverActor.behavior(promise, timeout)
            )
            _ <- KafkaQueryAndSubscribeActor(actorId, context)(
              topic,
              groupId,
              queryReceiver,
              brokers,
              subscribe = false,
              query = true
            )
          } yield state

        case QueryAsync(topic, groupId, replyTo) => KafkaQueryAndSubscribeActor(innerQueryActorId, context)(
            topic,
            groupId,
            replyTo,
            brokers,
            subscribe = false,
            query = true
          ).as(state)

        case Subscribe(topic, groupId, replyTo) => KafkaQueryAndSubscribeActor(innerQueryActorId, context)(
            topic,
            groupId,
            replyTo,
            brokers,
            subscribe = true,
            query = false
          ).as(state)
        case Stop => context.stopSelf.as(state)
        case PublishMessage(topic, key, message) => state.fold(Task(()))(_ ! PublishMessageToKafka(topic, key, message))
            .as(state)
        case CreateTopicIfMissing(topic, topicConfig) => AdminClient.make(AdminClientSettings(brokers)).use(
            _.createTopic(AdminClient.NewTopic(topic, topicConfig.numPartitions, topicConfig.replicationFactor)).fold(
              {
                case _: TopicExistsException => Task.unit
                case x                       => Task.fail(x)
              },
              _ => Task.unit
            )
          ).as(state)
      }
    }.withInit { (_, context, initState, _) =>
      for {
        publishActor <- initState.map(RIO(_))
          .getOrElse(context.make("KafkaPublishActor", ActorConfig(), (), KafkaPublishActor.behavior[R](brokers)))
      } yield (Seq.empty, Seq.empty, Some(publishActor))
    }

  private def getQueryActorId = { RIO.effectTotal(innerQueryActorId) }

  private def innerQueryActorId = { s"queryAndSubscribe-${UUID.randomUUID()}" }
}
