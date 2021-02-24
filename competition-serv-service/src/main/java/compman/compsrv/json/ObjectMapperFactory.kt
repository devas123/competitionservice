package compman.compsrv.json

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdJdkSerializers
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.util.concurrent.atomic.AtomicLong

object ObjectMapperFactory {
    fun createObjectMapper(): ObjectMapper {
        val builder = Jackson2ObjectMapperBuilder()
        createJacksonMapperCustomizer().customize(builder)
        return builder.build()
    }

    fun createJacksonMapperCustomizer() = Jackson2ObjectMapperBuilderCustomizer { mapper ->
        mapper.featuresToEnable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        mapper.featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper.visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        mapper.serializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.modulesToInstall(
            ParameterNamesModule(),
            Jdk8Module(),
            JavaTimeModule(),
            KotlinModule(),
            SimpleModule().also { module ->
            module.addDeserializer(CommandDTO::class.java, PlymorphicCommandDeserializer())
            module.addDeserializer(EventDTO::class.java, PolymorphicEventDeserializer())
            module.addSerializer(AtomicLong::class.java, StdJdkSerializers.AtomicLongSerializer())
        })
    }
}