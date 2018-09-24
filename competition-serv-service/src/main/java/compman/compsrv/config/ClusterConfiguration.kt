package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.cluster.ZookeeperSession
import compman.compsrv.service.CategoryStateService
import compman.compsrv.service.ScheduleService
import compman.compsrv.validators.CategoryCommandsValidatorRegistry
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
open class ClusterConfiguration {


    @Bean(destroyMethod = "close")
    open fun zookeeperSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                              kafkaProperties: KafkaProperties,
                              competitionStateService: CategoryStateService,
                              scheduleService: ScheduleService,
                              restTemplate: RestTemplate,
                              matCommandsValidatorRegistry: MatCommandsValidatorRegistry,
                              validatorRegistry: CategoryCommandsValidatorRegistry) =
            ZookeeperSession(clusterConfigurationProperties,
                    kafkaProperties,
                    competitionStateService,
                    scheduleService,
                    restTemplate,
                    validatorRegistry,
                    matCommandsValidatorRegistry)

    @Bean
    open fun stateQueryService(zookeeperSession: ZookeeperSession) = zookeeperSession.stateQueryService

}