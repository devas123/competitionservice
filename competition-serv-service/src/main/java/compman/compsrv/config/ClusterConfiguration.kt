package compman.compsrv.config

import compman.compsrv.cluster.ClusterSession
import compman.compsrv.service.StateQueryService
import compman.compsrv.service.saga.SagaManager
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.ClusterConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.web.client.RestTemplate
import kotlin.concurrent.thread

@Configuration
@EnableConfigurationProperties(ClusterConfigurationProperties::class)
open class ClusterConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterConfiguration::class.java)
    }


    @Bean(destroyMethod = "shutdown")
    open fun cluster(clusterConfigurationProperties: ClusterConfigurationProperties): Cluster {
        val clusterConfig = ClusterConfig.defaultConfig()
        val cluster = Cluster.joinAwait(clusterConfig)
        Runtime.getRuntime().addShutdownHook(thread(start = false) { cluster.shutdown() })
        log.info("Started instance at ${cluster.address().host()}:${cluster.address().port()}")
        return cluster
    }

    @Bean
    @DependsOn("cluster")
    open fun zookeeperSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                              restTemplate: RestTemplate,
                              cluster: Cluster) =
            ClusterSession(clusterConfigurationProperties,
                    restTemplate,
                    cluster)

    @Bean
    open fun sagaFactory(stateQueryService: StateQueryService) = SagaManager(stateQueryService)

    @Bean
    open fun stateQueryService(zookeeperSession: ClusterSession) = zookeeperSession.stateQueryService
}