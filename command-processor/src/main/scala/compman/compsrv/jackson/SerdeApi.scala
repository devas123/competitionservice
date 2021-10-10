package compman.compsrv.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.common.header.Headers
import zio.{RIO, Task}
import zio.kafka.serde.{Deserializer, Serializer}

object SerdeApi {

  val objectMapper: ObjectMapper = ObjectMapperFactory.createObjectMapper

  val byteSerializer: Serializer[Any, Array[Byte]] =
    new Serializer[Any, Array[Byte]] {
      override def serialize(
          topic: String,
          headers: Headers,
          value: Array[Byte]
      ): RIO[Any, Array[Byte]] = RIO {
        value
      }

      override def configure(props: Map[String, AnyRef], isKey: Boolean): Task[Unit] = Task.unit
    }
  val commandDeserializer: Deserializer[Any, CommandDTO] =
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
  val eventDeserializer: Deserializer[Any, EventDTO] =
    new Deserializer[Any, EventDTO] {

      override def configure(props: Map[String, AnyRef], isKey: Boolean): Task[Unit] = Task.unit

      override def deserialize(
          topic: String,
          headers: Headers,
          data: Array[Byte]
      ): RIO[Any, EventDTO] = RIO {
        objectMapper.readValue(data, classOf[EventDTO])
      }
    }

}
