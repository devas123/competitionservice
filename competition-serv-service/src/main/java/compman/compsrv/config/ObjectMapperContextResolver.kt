package compman.compsrv.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.ws.rs.ext.ContextResolver
import javax.ws.rs.ext.Provider

@Provider
@Component
class ObjectMapperContextResolver : ContextResolver<ObjectMapper> {

    @Autowired
    private lateinit var mapper: ObjectMapper


    override fun getContext(type: Class<*>): ObjectMapper {
        return mapper
    }
}