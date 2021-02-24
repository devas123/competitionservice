package compman.compsrv

import com.compman.starter.properties.CommunicationProperties
import com.compman.starter.properties.KafkaProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.web.server.WebFilter


@SpringBootApplication(scanBasePackages = ["compman.compsrv"])
@EnableConfigurationProperties(KafkaProperties::class, CommunicationProperties::class)
@EnableCaching
class CompetitionServiceApplication {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionServiceApplication::class.java)
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(CompetitionServiceApplication::class.java, *args)
        }
    }

    @Bean
    fun loggingFilter(): WebFilter =
        WebFilter { exchange, chain ->
            val request = exchange.request
//            log.info("Processing request method=${request.method} path=${request.path.pathWithinApplication()} params=[${request.queryParams}] body=[${request.body}]")
            val result = chain.filter(exchange)
//            log.info("Handling with response ${exchange.response}")
            return@WebFilter result
        }

}