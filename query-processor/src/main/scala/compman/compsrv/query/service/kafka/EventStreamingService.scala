package compman.compsrv.query.service.kafka

import zio.{Has, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.stream.ZStream

import java.util.UUID

object EventStreamingService {

  trait EventStreaming[R] {
    def getByteArrayStream(topic: String): ZStream[R, Throwable, Array[Byte]]
  }

  def live(bootstrapServers: List[String]): EventStreaming[Clock with Blocking] =
    (topic: String) => {
      val settings: ConsumerSettings = ConsumerSettings(bootstrapServers).withGroupId(UUID.randomUUID().toString)
        .withClientId(UUID.randomUUID().toString)
      val layer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]] = Consumer.make(settings).toLayer
      Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray).map(_.value)
        .provideSomeLayer(layer)
    }

  def test(stream: Map[String, List[Array[Byte]]]): EventStreaming[Any] =
    (topic: String) => { ZStream.fromIterable(stream.getOrElse(topic, List.empty)) }

}
