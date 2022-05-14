package compman.compsrv.jackson

import compservice.model.protobuf.command.Command
import compservice.model.protobuf.event.Event
import org.apache.kafka.common.header.Headers
import zio.RIO
import zio.kafka.serde.Deserializer

object SerdeApi {
  val commandDeserializer: Deserializer[Any, Command] =
    (_: String, _: Headers, data: Array[Byte]) => RIO {
      Command.parseFrom(data)
    }
  val eventDeserializer: Deserializer[Any, Event] =
    (_: String, _: Headers, data: Array[Byte]) => RIO {
      Event.parseFrom(data)
    }
}
