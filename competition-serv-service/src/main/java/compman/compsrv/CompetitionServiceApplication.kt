package compman.compsrv

import com.compman.starter.properties.CommunicationProperties
import com.compman.starter.properties.KafkaProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.filter.CommonsRequestLoggingFilter


@SpringBootApplication
@EnableConfigurationProperties(KafkaProperties::class, CommunicationProperties::class)
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = ["compman.compsrv.repository"])
@EnableCaching
class CompetitionServiceApplication {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            SpringApplication.run(CompetitionServiceApplication::class.java, *args)
        }
    }

    @Bean
    fun logFilter(): CommonsRequestLoggingFilter {
        val filter = CommonsRequestLoggingFilter()
        filter.setIncludeQueryString(true)
        filter.setIncludePayload(true)
        filter.setMaxPayloadLength(10000)
        filter.setIncludeHeaders(true)
        filter.setAfterMessagePrefix("REQUEST DATA : ")
        return filter
    }

}