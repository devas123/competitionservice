package compman.compsrv.config

import com.fasterxml.jackson.databind.ObjectMapper
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