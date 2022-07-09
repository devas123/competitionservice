package compman.compsrv.logic.actor.kafka

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import akka.stream.Materializer
import org.apache.kafka.clients.producer.ProducerRecord

object KafkaPublishActor {
  sealed trait KafkaPublishActorCommand

  case object Stop extends KafkaPublishActorCommand

  private[kafka] case class PublishMessageToKafka(topic: String, key: String, message: Array[Byte])
      extends KafkaPublishActorCommand

  def behavior(producerSettings: ProducerSettings[String, Array[Byte]])(implicit
    materializer: Materializer
  ): Behavior[KafkaPublishActorCommand] = {
    val producer = producerSettings.createKafkaProducer()
    val sink     = Producer.plainSink(producerSettings.withProducer(producer))
    Behaviors.receive((context, command) =>
      command match {
        case PublishMessageToKafka(topic, key, message) =>
          context.log.info(s"Publish message to $topic")
          Source.single(message).map(value => new ProducerRecord[String, Array[Byte]](topic, key, value)).runWith(sink)
          Behaviors.same
        case Stop =>
          context.stop(context.self)
          Behaviors.same
      }
    )
  }
}
