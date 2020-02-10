package compman.compsrv.config

import org.jooq.conf.RenderNameStyle
import org.jooq.conf.Settings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration()
class DSLContextConfiguration {
    @Bean
    fun settings(): Settings =
            Settings().withRenderNameStyle(RenderNameStyle.AS_IS)
}