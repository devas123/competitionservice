package compman.compsrv.logic

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.config.AppConfig
import compman.compsrv.jackson.ObjectMapperFactory
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import fs2.{Pipe, Stream}
import org.apache.kafka.common.header.Headers
import zio.{RIO, Task}
import zio.kafka.serde.{Deserializer, Serializer}

object CommunicationApi {

  val objectMapper: ObjectMapper = ObjectMapperFactory.createObjectMapper

  trait KafkaApi[F[+_]] {
    def inboundStream[A](config: AppConfig): Stream[F, CommandDTO]
    def outboundStream[A](config: AppConfig): Pipe[F, EventDTO, Unit]
  }

  object KafkaApi {
    def apply[F[+_]](implicit F: KafkaApi[F]): KafkaApi[F] = F
  }

  val serializer: Serializer[Any, EventDTO] =
    new Serializer[Any, EventDTO] {
      override def serialize(
          topic: String,
          headers: Headers,
          value: EventDTO
      ): RIO[Any, Array[Byte]] = RIO {
        objectMapper.writeValueAsBytes(value)
      }

      override def configure(props: Map[String, AnyRef], isKey: Boolean): Task[Unit] = Task.unit
    }
  val deserializer: Deserializer[Any, CommandDTO] =
    new Deserializer[Any, CommandDTO] {

      override def configure(props: Map[String, AnyRef], isKey: Boolean): Task[Unit] = Task.unit

      override def deserialize(
          topic: String,
          headers: Headers,
          data: Array[Byte]
      ): RIO[Any, CommandDTO] = RIO {
        objectMapper.readValue(data, classOf[CommandDTO])
      }
    }

}
