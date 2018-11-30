package compman.compsrv.kafka.serde

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

class JsonSerde<T>(private val clazz: Class<T>, private val objectMapper: ObjectMapper) : Serde<T> {

    override fun configure(p0: MutableMap<String, *>?, p1: Boolean) {

    }

    override fun deserializer(): Deserializer<T> = object : Deserializer<T> {
        override fun configure(p0: MutableMap<String, *>?, p1: Boolean) {

        }

        override fun deserialize(topic: String?, bytes: ByteArray?): T? {
            return try {
                bytes?.let {
                    objectMapper.readValue(it, clazz)
                }
            } catch (e: Exception) {
                throw SerializationException("Error deserializing JSON message", e)
            }
        }

        override fun close() {
        }
    }

    override fun close() {

    }

    override fun serializer(): Serializer<T> = object : Serializer<T> {
        override fun configure(p0: MutableMap<String, *>?, p1: Boolean) {

        }

        override fun serialize(topic: String?, data: T?): ByteArray? {
            return try {
                data?.let {
                    objectMapper.writeValueAsBytes(it)
                }
            } catch (e: Exception) {
                throw SerializationException("Error serializing message to JSON", e)
            }
        }

        override fun close() {
        }

    }
}