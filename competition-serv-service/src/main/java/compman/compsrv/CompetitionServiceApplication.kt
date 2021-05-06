package compman.compsrv

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import properties.CommunicationProperties
import properties.KafkaProperties


@SpringBootApplication(scanBasePackages = ["compman.compsrv"])
@EnableConfigurationProperties(KafkaProperties::class, CommunicationProperties::class)
@EnableCaching
class CompetitionServiceApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(CompetitionServiceApplication::class.java, *args)
        }
    }
}