package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.repository.CategoryCrudRepository
import compman.compsrv.repository.CompetitionStateCrudRepository
import compman.compsrv.service.StateQueryService
import compman.compsrv.service.saga.SagaManager
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.ClusterConfig
import io.scalecube.transport.Address
import io.scalecube.transport.TransportConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.web.client.RestTemplate
import kotlin.concurrent.thread

@Configuration
@EnableConfigurationProperties(ClusterConfigurationProperties::class)
class ClusterConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterConfiguration::class.java)
    }


    @Bean(destroyMethod = "shutdown")
    fun cluster(clusterConfigurationProperties: ClusterConfigurationProperties): Cluster {
        val memberHost = if (clusterConfigurationProperties.advertisedHost?.isBlank() != false || clusterConfigurationProperties.advertisedHost == "localhost") {
            ClusterConfig.DEFAULT_MEMBER_HOST
        } else {
            clusterConfigurationProperties.advertisedHost
        }
        val clusterSeed = clusterConfigurationProperties.clusterSeed?.mapNotNull { s -> Address.from(s) } ?: emptyList()
        val clusterConfig = ClusterConfig.builder()
                .transportConfig(TransportConfig.builder()
                        .port(clusterConfigurationProperties.advertisedPort).build())
                .seedMembers(clusterSeed)
                .memberHost(memberHost)
                .build()
        val cluster = Cluster.joinAwait(clusterConfig)
        Runtime.getRuntime().addShutdownHook(thread(start = false) { cluster.shutdown() })
        log.info("Started instance at ${cluster.address().host()}:${cluster.address().port()}")
        log.info("Members of the cluster: ")
        cluster.members()?.forEach {
            log.info("${it.id()} -> ${it.address()}")
        }
        return cluster
    }

    @Bean(initMethod = "init")
    @DependsOn("cluster")
    fun clusterSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                       restTemplate: RestTemplate,
                       cluster: Cluster,
                       adminClient: KafkaAdminUtils,
                       competitionStateCrudRepository: CompetitionStateCrudRepository,
                       categoryStateCrudRepository: CategoryCrudRepository,
                       kafkaProperties: KafkaProperties) =
            ClusterSession(clusterConfigurationProperties,
                    restTemplate,
                    cluster,
                    adminClient, competitionStateCrudRepository, categoryStateCrudRepository, kafkaProperties)

    @Bean
    fun sagaFactory(stateQueryService: StateQueryService) = SagaManager(stateQueryService)

    @Bean
    fun stateQueryService(clusterSession: ClusterSession, clusterConfigurationProperties: ClusterConfigurationProperties) = StateQueryService(clusterSession, clusterConfigurationProperties)
}