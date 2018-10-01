package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.cluster.LeaderElection
import compman.compsrv.cluster.ZookeeperLeaderElection
import compman.compsrv.cluster.ZookeeperSession
import compman.compsrv.service.CategoryStateService
import compman.compsrv.service.ScheduleService
import compman.compsrv.validators.CategoryCommandsValidatorRegistry
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.client.RestTemplate

@Configuration
class ClusterConfiguration {


    @Bean(destroyMethod = "close")
    fun zookeeperSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                         kafkaProperties: KafkaProperties,
                         competitionStateService: CategoryStateService,
                         scheduleService: ScheduleService,
                         restTemplate: RestTemplate,
                         matCommandsValidatorRegistry: MatCommandsValidatorRegistry,
                         validatorRegistry: CategoryCommandsValidatorRegistry,
                         leaderElection: LeaderElection) =
            ZookeeperSession(clusterConfigurationProperties,
                    kafkaProperties,
                    competitionStateService,
                    scheduleService,
                    restTemplate,
                    validatorRegistry,
                    matCommandsValidatorRegistry,
                    leaderElection)

    @Bean
    fun stateQueryService(zookeeperSession: ZookeeperSession) = zookeeperSession.stateQueryService

    @Bean
    @Profile("el-zookeeper")
    fun zookeeperLeaderElection(clusterConfigurationProperties: ClusterConfigurationProperties) = ZookeeperLeaderElection(clusterConfigurationProperties)

}