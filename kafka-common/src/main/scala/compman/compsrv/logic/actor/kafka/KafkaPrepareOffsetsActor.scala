package compman.compsrv.logic.actor.kafka

import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscription}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object KafkaPrepareOffsetsActor {

  sealed trait KafkaPrepareOffsetsActorApi

  final case class OffsetsRetrievalFailed(exception: Throwable) extends KafkaPrepareOffsetsActorApi
  final case class OffsetsRetrievalSuccessful(offsets: StartOffsetsAndTopicEndOffset)
      extends KafkaPrepareOffsetsActorApi

  def behavior(
    consumerSettings: ConsumerSettings[String, Array[Byte]],
    actorToStart: ActorRef[KafkaSubscribeActor.KafkaSubscribeActorApi],
    topic: String,
    desiredStartOffset: Map[Int, Long],
    subscriptionFactory: StartOffsetsAndTopicEndOffset => Subscription
  ): Behavior[KafkaPrepareOffsetsActorApi] = Behaviors.setup { context =>
    implicit val dispatcher: ExecutionContextExecutor = context.executionContext
    implicit val classicActorSystem: ActorSystem      = context.system.classicSystem
    context.watch(actorToStart)
    context.pipeToSelf(OffsetsRetrievalFeature.prepareOffsetsForConsumer(consumerSettings)(topic, desiredStartOffset)) {
      case Failure(exception) => OffsetsRetrievalFailed(exception)
      case Success(value) => value match {
          case Some(value) => OffsetsRetrievalSuccessful(value)
          case None        => OffsetsRetrievalFailed(new Exception("Empty offsets received"))
        }
    }
    Behaviors.receiveMessage[KafkaPrepareOffsetsActorApi] {
      case OffsetsRetrievalFailed(exception) =>
        context.log.error("Error while retrieving offsets.", exception)
        actorToStart ! KafkaSubscribeActor.Stop
        Behaviors.stopped
      case OffsetsRetrievalSuccessful(offsets) =>
        actorToStart ! KafkaSubscribeActor.Start(offsets, subscriptionFactory(offsets))
        Behaviors.stopped
    }.receiveSignal { case (ctx, Terminated(value)) =>
      ctx.log.info(s"Subscriber actor ($value) stopped before retrieving offsets. Stopping.")
      Behaviors.stopped
    }
  }

}
