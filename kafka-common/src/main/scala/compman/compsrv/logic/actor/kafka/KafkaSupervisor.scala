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

  final case class QueryFinished(count: Long) extends KafkaConsumerApi

  final case class QueryError(error: Throwable) extends KafkaConsumerApi

  final case class MessageReceived(topic: String, committableRecord: CommittableRecord[String, Array[Byte]])
      extends KafkaConsumerApi

  sealed trait KafkaSupervisorCommand

  case class QueryAndSubscribe(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    startOffset: Option[Long] = Some(0),
    endOffset: Option[Long] = None,
    uuid: String = UUID.randomUUID().toString
  ) extends KafkaSupervisorCommand

  case class CreateTopicIfMissing(topic: String, topicConfig: KafkaTopicConfig) extends KafkaSupervisorCommand

  case class QueryAsync(topic: String, groupId: String, replyTo: ActorRef[KafkaConsumerApi], startOffset: Option[Long] = Some(0), endOffset: Option[Long] = None)
      extends KafkaSupervisorCommand

  private case class SubscriberStopped(uuid: String) extends KafkaSupervisorCommand

  case class QuerySync(
    topic: String,
    groupId: String,
    promise: Promise[Throwable, Seq[Array[Byte]]],
    timeout: Duration = 10.seconds,
    startOffset: Option[Long] = Some(0),
    endOffset: Option[Long] = None
  ) extends KafkaSupervisorCommand

  case class Subscribe(
    topic: String,
    groupId: String,
    replyTo: ActorRef[KafkaConsumerApi],
    uuid: String = UUID.randomUUID().toString,
    startOffset: Option[Long] = None
  ) extends KafkaSupervisorCommand

  case class Unsubscribe(uuid: String) extends KafkaSupervisorCommand

  case class PublishMessage(topic: String, key: String, message: Array[Byte]) extends KafkaSupervisorCommand {}
  object PublishMessage {
    def apply(tuple: (String, String, Array[Byte])): PublishMessage = PublishMessage(tuple._1, tuple._2, tuple._3)
  }

  case object Stop extends KafkaSupervisorCommand

  type KafkaSupervisorEnvironment[R] = R with Logging with Clock with Blocking with Console

  final case class KafkaSupervisorState(
    publishActor: Option[ActorRef[KafkaPublishActor.KafkaPublishActorCommand]],
    queryAndSubscribeActors: Map[String, ActorRef[KafkaQueryAndSubscribeActor.KafkaQueryActorCommand]]
  )

  val initialState: KafkaSupervisorState = KafkaSupervisorState(None, Map.empty)

  import Behaviors._
  def behavior[R: Tag](
    brokers: List[String]
  ): ActorBehavior[KafkaSupervisorEnvironment[R], KafkaSupervisorState, KafkaSupervisorCommand] = Behaviors
    .behavior[KafkaSupervisorEnvironment[R], KafkaSupervisorState, KafkaSupervisorCommand]
    .withReceive { (context, _, state, command, _) =>
      command match {
        case Unsubscribe(uuid) => state.queryAndSubscribeActors.get(uuid).map(a => a ! KafkaQueryAndSubscribeActor.Stop)
            .getOrElse(RIO.unit).as(state)
        case SubscriberStopped(uuid) => RIO
            .effect(state.copy(queryAndSubscribeActors = state.queryAndSubscribeActors - uuid))
        case QueryAndSubscribe(topic, groupId, replyTo, startOffset, endOffset, uuid) => for {
            _ <- state.queryAndSubscribeActors.get(uuid).map(a => a ! KafkaQueryAndSubscribeActor.Stop)
              .getOrElse(RIO.unit)
            actorId = innerQueryActorId
            actor <- KafkaQueryAndSubscribeActor(actorId, context)(
              topic,
              groupId,
              replyTo,
              brokers,
              subscribe = true,
              query = true,
              startOffset = startOffset,
              endOffset = endOffset
            )
            _ <- context.watchWith(SubscriberStopped(uuid), actor)
            _ <- Logging.info(s"Created actor with id $actorId to process query and subscribe request.")
          } yield state.copy(queryAndSubscribeActors = state.queryAndSubscribeActors + (uuid -> actor))
        case QuerySync(topic, groupId, promise, timeout, startOffset, endOffset) => for {
            queryReceiver <- context.make(
              UUID.randomUUID().toString,
              ActorConfig(),
              Seq.empty[Array[Byte]],
              KafkaSyncQueryReceiverActor.behavior(promise, timeout)
            )
            actorId = innerQueryActorId
            _ <- KafkaQueryAndSubscribeActor(actorId, context)(
              topic,
              groupId,
              queryReceiver,
              brokers,
              subscribe = false,
              query = true,
              startOffset,
              endOffset
            )
          } yield state

        case QueryAsync(topic, groupId, replyTo, startOffset, endOffset) => KafkaQueryAndSubscribeActor(
            innerQueryActorId,
            context
          )(topic, groupId, replyTo, brokers, subscribe = false, query = true, startOffset, endOffset).as(state)

        case Subscribe(topic, groupId, replyTo, uuid, startOffset) => for {
            _ <- state.queryAndSubscribeActors.get(uuid).map(a => a ! KafkaQueryAndSubscribeActor.Stop)
              .getOrElse(RIO.unit)
            actor <- KafkaQueryAndSubscribeActor(innerQueryActorId, context)(
              topic,
              groupId,
              replyTo,
              brokers,
              subscribe = true,
              query = false,
              startOffset,
              None
            )
            _ <- context.watchWith(SubscriberStopped(uuid), actor)
          } yield state.copy(queryAndSubscribeActors = state.queryAndSubscribeActors + (uuid -> actor))
        case Stop => context.stopSelf.as(state)
        case PublishMessage(topic, key, message) => state.publishActor
            .fold(Task(()))(_ ! PublishMessageToKafka(topic, key, message)).as(state)
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
        publishActor <- initState.publishActor.map(RIO(_))
          .getOrElse(context.make("KafkaPublishActor", ActorConfig(), (), KafkaPublishActor.behavior[R](brokers)))
      } yield (Seq.empty, Seq.empty, initState.copy(publishActor = Some(publishActor)))
    }

  private def innerQueryActorId = { s"queryAndSubscribe-${UUID.randomUUID()}" }
}
