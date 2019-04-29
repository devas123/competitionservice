package compman.compsrv.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule

object ObjectMapperFactory {
    fun createObjectMapper(): ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(ParameterNamesModule())
            .registerModule(Jdk8Module())
            .registerModule(JavaTimeModule())
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .enableDefaultTyping(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, JsonTypeInfo.As.PROPERTY)
}