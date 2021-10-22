package compman.compsrv.query.service.kafka

import compman.compsrv.model.events.EventDTO
import compman.compsrv.query.sede.SerdeApi
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import zio.{Has, RIO, Task, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer._
import zio.kafka.serde.Serde
import zio.logging.Logging
import zio.stream.ZStream

import java.util.UUID

object EventStreamingService {

  trait EventStreaming[R] {
    def getLastOffsets(topic: String, groupId: String): RIO[R, Map[TopicPartition, Long]]
    def retreiveEvents(topic: String, groupId: String, endOffsets: Map[TopicPartition, Long]): RIO[R, List[EventDTO]]
    def getByteArrayStream(
      topic: String,
      groupId: String
    ): ZStream[R, Throwable, CommittableRecord[String, Array[Byte]]]
  }

  def live(consumerSettings: ConsumerSettings): EventStreaming[Clock with Blocking with Logging] = {
    new EventStreaming[Clock with Blocking with Logging] {

      import cats.implicits._
      import zio.interop.catz._

      override def retreiveEvents(
        topic: String,
        groupId: String,
        endOffsets: Map[TopicPartition, Long]
      ): RIO[Clock with Blocking with Logging, List[EventDTO]] = (for {
        offset          <- Consumer.beginningOffsets(endOffsets.keySet)
        filteredOffsets <- RIO(endOffsets.filter(_._2 > 0))
        _ <- Logging.info(s"Getting events from topic $topic, endOffsets: $endOffsets, start from $offset")
        res <-
          if (filteredOffsets.nonEmpty) {
            for {
              _ <- Logging.info(s"Filtered offsets: $filteredOffsets")
              off = filteredOffsets.keySet.map(tp => (tp.topic(), tp.partition()))
              _ <- Logging.info(s"Off: $off")
              res1 <- off.toList.traverse(o => {
                val partition = new TopicPartition(o._1, o._2)
                Consumer.subscribeAnd(Subscription.manual(o)).plainStream(Serde.string, SerdeApi.eventDeserializer)
                  .take(endOffsets(partition) - offset(partition)).runCollect
              })
              _ <- res1.traverse(chunk => chunk.map(_.offset).foldLeft(OffsetBatch.empty)(_ merge _).commit)

            } yield res1.flatMap(_.toList.map(_.value))
          } else { ZIO.effectTotal(List.empty) }
        _ <- Logging.info("Done collecting events.")
      } yield res).provideSomeLayer[Clock with Blocking with Logging](Consumer.make(consumerSettings).toLayer)

      override def getByteArrayStream(
        topic: String,
        groupId: String
      ): ZStream[Clock with Blocking with Logging, Throwable, CommittableRecord[String, Array[Byte]]] = {
        val settings: ConsumerSettings = ConsumerSettings(consumerSettings.bootstrapServers).withGroupId(groupId)
          .withClientId(UUID.randomUUID().toString)
          .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(Consumer.AutoOffsetStrategy.Earliest))
        val layer: ZLayer[Clock with Blocking with Logging, Throwable, Has[Consumer.Service]] = Consumer.make(settings)
          .toLayer
        Consumer.subscribeAnd(Subscription.topics(topic)).plainStream(Serde.string, Serde.byteArray)
          .provideSomeLayer(layer)
      }

      override def getLastOffsets(
        topic: String,
        groupId: String
      ): RIO[Clock with Blocking with Logging, Map[TopicPartition, Long]] = {
        (Consumer.partitionsFor(topic) >>=
          (pt => Consumer.endOffsets(pt.map(pi => new TopicPartition(pi.topic(), pi.partition())).toSet)))
          .provideSomeLayer[Clock with Blocking with Logging](Consumer.make(consumerSettings).toLayer)
      }
    }
  }

  def test(stream: Map[String, List[Array[Byte]]], events: List[EventDTO] = List.empty): EventStreaming[Any] =
    new EventStreaming[Any] {
      override def getLastOffsets(topic: String, groupId: String): RIO[Any, Map[TopicPartition, Long]] = RIO(Map.empty)

      override def retreiveEvents(
        topic: String,
        groupId: String,
        endOffsets: Map[TopicPartition, Long]
      ): RIO[Any, List[EventDTO]] = RIO(events)

      override def getByteArrayStream(
        topic: String,
        groupId: String
      ): ZStream[Any, Throwable, CommittableRecord[String, Array[Byte]]] = {
        ZStream.fromIterable(stream.getOrElse(topic, List.empty))
          .map(arr => CommittableRecord(new ConsumerRecord(topic, 0, 0, "id", arr), _ => Task.unit))
      }
    }
}
