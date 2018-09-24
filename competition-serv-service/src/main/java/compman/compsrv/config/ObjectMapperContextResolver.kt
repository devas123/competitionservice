package compman.compsrv.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import compman.compsrv.json.ObjectMapperFactory
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

@Provider
open class ObjectMapperContextResolver : ContextResolver<ObjectMapper> {

    private val mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper()


    override fun getContext(type: Class<*>): ObjectMapper {
        return mapper
    }
}