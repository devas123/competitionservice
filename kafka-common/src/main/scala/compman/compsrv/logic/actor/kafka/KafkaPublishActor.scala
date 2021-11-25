package compman.compsrv.logic.actor.kafka

import compman.compsrv.logic.actor.kafka.KafkaSupervisor.KafkaSupervisorEnvironment
import compman.compsrv.logic.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import org.apache.kafka.clients.producer.ProducerRecord
import zio.{Chunk, RIO, Tag}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde
import zio.logging.Logging

object KafkaPublishActor {
  sealed trait KafkaPublishActorCommand[+_]

  case object Stop extends KafkaPublishActorCommand[Unit]

  private[kafka] case class PublishMessageToKafka(topic: String, key: String, message: Array[Byte])
      extends KafkaPublishActorCommand[Unit]

  type KafkaPublishActorEnvironment[R] = R with Logging with Clock with Blocking

  def behavior[R: Tag](
    brokers: List[String]
  ): ActorBehavior[KafkaPublishActorEnvironment[R], Unit, KafkaPublishActorCommand] =
    new ActorBehavior[KafkaPublishActorEnvironment[R], Unit, KafkaPublishActorCommand] {
      override def receive[A](
        context: Context[KafkaPublishActorCommand],
        actorConfig: ActorConfig,
        state: Unit,
        command: KafkaPublishActorCommand[A],
        timers: Timers[KafkaPublishActorEnvironment[R], KafkaPublishActorCommand]
      ): RIO[KafkaPublishActorEnvironment[R], (Unit, A)] = {
        command match {
          case PublishMessageToKafka(topic, key, message) => for {
              _ <- Producer.produceChunk(Chunk.single(new ProducerRecord[String, Array[Byte]](topic, key, message)))
              _ <- Logging.info(s"Published message to $topic with key $key")
            } yield ((), ().asInstanceOf[A])
          case Stop => context.stopSelf.as(((), ().asInstanceOf[A]))
        }
      }.provideSomeLayer[KafkaSupervisorEnvironment[R]](
        Producer.make[R, String, Array[Byte]](ProducerSettings(brokers), Serde.string, Serde.byteArray).toLayer
      )
    }
}
