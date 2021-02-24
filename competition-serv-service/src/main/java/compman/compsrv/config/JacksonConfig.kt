package compman.compsrv.config

import compman.compsrv.json.ObjectMapperFactory
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

    @Bean
    fun createJackson2ObjectMapperBuilderCustomizer(): Jackson2ObjectMapperBuilderCustomizer {
        return ObjectMapperFactory.createJacksonMapperCustomizer()
    }
}