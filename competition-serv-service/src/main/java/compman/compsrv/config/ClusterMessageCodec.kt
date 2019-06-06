package compman.compsrv.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.scalecube.transport.Message
import io.scalecube.transport.MessageCodec
import java.io.InputStream
import java.io.OutputStream

class ClusterMessageCodec(private val mapper: ObjectMapper) : MessageCodec {
    override fun deserialize(stream: InputStream?): Message {
        return mapper.readValue(stream, Message::class.java)
    }
    override fun serialize(message: Message?, stream: OutputStream?) {
        mapper.writeValue(stream, message)
    }
}