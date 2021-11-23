package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.actors.{ActorBehavior, Context, Timers}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.producer.Producer
import zio.logging.Logging
import zio.{RIO, Tag}

object KafkaPublishActor {
  sealed trait KafkaPublishActorCommand[+_]

  case object Stop extends KafkaPublishActorCommand[Unit]

  private[kafka] case class PublishMessageToKafka(topic: String, key: String, message: Array[Byte]) extends KafkaPublishActorCommand[Unit]

  type KafkaPublishActorEnvironment[R] = R with Logging with Clock with Blocking with Producer[R, String, Array[Byte]]

  def behavior[R: Tag]: ActorBehavior[KafkaPublishActorEnvironment[R], Unit, KafkaPublishActorCommand] =
    new ActorBehavior[KafkaPublishActorEnvironment[R], Unit, KafkaPublishActorCommand] {
      override def receive[A](
                               context: Context[KafkaPublishActorCommand],
                               actorConfig: ActorConfig,
                               state: Unit,
                               command: KafkaPublishActorCommand[A],
                               timers: Timers[KafkaPublishActorEnvironment[R], KafkaPublishActorCommand]
                             ): RIO[KafkaPublishActorEnvironment[R], (Unit, A)] =
        command match {
          case PublishMessageToKafka(topic, key, message) =>
            Logging.info(s"Sending message to kafka: $topic, $key, ${message.mkString("Array(", ", ", ")")}") *> zio.kafka.producer.Producer
              .produce(topic, key, message).as(((), ().asInstanceOf[A]))
          case Stop => context.stopSelf.as(((), ().asInstanceOf[A]))
        }
    }
}
