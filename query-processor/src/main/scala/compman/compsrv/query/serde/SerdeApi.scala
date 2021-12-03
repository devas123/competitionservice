package compman.compsrv.query.serde

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import org.apache.kafka.common.header.Headers
import zio.RIO
import zio.kafka.serde.{Deserializer, Serializer}

object SerdeApi {

  val objectMapper: ObjectMapper = ObjectMapperFactory.createObjectMapper

  val byteSerializer: Serializer[Any, Array[Byte]] = (_: String, _: Headers, value: Array[Byte]) => RIO {
    value
  }
  val commandDeserializer: Deserializer[Any, CommandDTO] = (_: String, _: Headers, data: Array[Byte]) => RIO {
    objectMapper.readValue(data, classOf[CommandDTO])
  }
  val eventDeserializer: Deserializer[Any, EventDTO] = (_: String, _: Headers, data: Array[Byte]) => RIO {
    objectMapper.readValue(data, classOf[EventDTO])
  }

}
