package compman.compsrv.query.service.kafka

import zio.{Has, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.stream.ZStream

object EventStreamingService {

  trait EventStreaming[R] {
    def getByteArrayStream(topic: String): ZStream[R, Throwable, Array[Byte]]
  }

  def live(bootstrapServers: List[String]): EventStreaming[Clock with Blocking] =
    new EventStreaming[Clock with Blocking] {
      val settings: ConsumerSettings                                           = ConsumerSettings(bootstrapServers)
      val layer: ZLayer[Clock with Blocking, Throwable, Has[Consumer.Service]] = Consumer.make(settings).toLayer
      override def getByteArrayStream(topic: String): ZStream[Clock with Blocking, Throwable, Array[Byte]] = {
        Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray).map(_.value)
          .provideSomeLayer(layer)
      }
    }

  def test(stream: Map[String, List[Array[Byte]]]): EventStreaming[Any] =
    (topic: String) => { ZStream.fromIterable(stream.getOrElse(topic, List.empty)) }

}
