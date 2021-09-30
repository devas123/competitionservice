package compman.compsrv.gateway.json

import com.fasterxml.jackson.annotation.{JsonAutoDetect, JsonInclude, PropertyAccessor}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdJdkSerializers
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import compman.compsrv.model.commands.CommandDTO

import java.util.concurrent.atomic.AtomicLong

object ObjectMapperFactory {

  def createObjectMapper: ObjectMapper = {
    val mapper = new ObjectMapper().findAndRegisterModules()
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
    mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    val simpleModule = new SimpleModule()
    mapper.registerModule(simpleModule)
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module())
    mapper.registerModule(new JavaTimeModule())
    simpleModule.addSerializer(classOf[AtomicLong], new StdJdkSerializers.AtomicLongSerializer())
    mapper
  }

}
