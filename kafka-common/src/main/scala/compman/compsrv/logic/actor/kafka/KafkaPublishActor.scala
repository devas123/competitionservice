package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor.KafkaSupervisorEnvironment
import compman.compsrv.logic.actors.{ActorBehavior, Behaviors, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, Tag}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.logging.Logging

object KafkaPublishActor {
  sealed trait KafkaPublishActorCommand

  case object Stop extends KafkaPublishActorCommand

  private[kafka] case class PublishMessageToKafka(topic: String, key: String, message: Array[Byte])
      extends KafkaPublishActorCommand

  type KafkaPublishActorEnvironment[R] = R with Logging with Clock with Blocking with Console

  import Behaviors._

  def behavior[R: Tag](
    brokers: List[String]
  ): ActorBehavior[KafkaPublishActorEnvironment[R], Unit, KafkaPublishActorCommand] = Behaviors
    .behavior[KafkaPublishActorEnvironment[R], Unit, KafkaPublishActorCommand].withReceive(
      (
        context: Context[KafkaPublishActorCommand],
        _: ActorConfig,
        _: Unit,
        command: KafkaPublishActorCommand,
        _: Timers[KafkaPublishActorEnvironment[R], KafkaPublishActorCommand]
      ) =>
        {
          command match {
            case PublishMessageToKafka(topic, key, message) => for {
                _ <- Producer.produceChunk(
                  Chunk.single(new ProducerRecord[String, Array[Byte]](topic, key, message)),
                  Serde.string,
                  Serde.byteArray
                )
                _ <- Logging.info(s"Published message to $topic with key $key")
              } yield ()
            case Stop => context.stopSelf.unit
          }
        }.provideSomeLayer[KafkaSupervisorEnvironment[R]](Producer.make(ProducerSettings(brokers)).toLayer)
    )
}
