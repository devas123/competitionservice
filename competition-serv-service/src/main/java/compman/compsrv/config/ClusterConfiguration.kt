package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.repository.*
import compman.compsrv.service.StateQueryService
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.ClusterConfig
import io.scalecube.transport.Address
import io.scalecube.transport.TransportConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.web.ServerProperties
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
    fun cluster(clusterConfigurationProperties: ClusterConfigurationProperties, serverProperties: ServerProperties): Cluster {
        val memberHost = if (clusterConfigurationProperties.advertisedHost?.isBlank() != false) {
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
                .addMetadata(ClusterSession.REST_PORT_METADATA_KEY, serverProperties.port.toString())
                .build()
        val cluster = Cluster.joinAwait(clusterConfig)
        Runtime.getRuntime().addShutdownHook(thread(start = false) { cluster.shutdown() })
        log.info("Started instance at ${cluster.address().host()}:${cluster.address().port()} with rest port: ${serverProperties.port}")
        log.info("Members of the cluster: ")
        cluster.members()?.forEach {
            log.info("${it.id()} -> ${it.address()}, ${it.metadata()[ClusterSession.REST_PORT_METADATA_KEY]}")
        }
        return cluster
    }

    @Bean(initMethod = "init", destroyMethod = "stop")
    @DependsOn("cluster")
    fun clusterSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                       cluster: Cluster,
                       adminClient: KafkaAdminUtils,
                       kafkaProperties: KafkaProperties,
                       serverProperties: ServerProperties,
                       competitionStateRepository: CompetitionStateRepository) =
            ClusterSession(clusterConfigurationProperties,
                    cluster,
                    adminClient, kafkaProperties, serverProperties, competitionStateRepository)

    @Bean
    fun stateQueryService(restTemplate: RestTemplate,
                          clusterSession: ClusterSession,
                          categoryStateCrudRepository: CategoryCrudRepository,
                          competitionStateCrudRepository: CompetitionStateCrudRepository,
                          scheduleCrudRepository: ScheduleCrudRepository,
                          competitorCrudRepository: CompetitorCrudRepository,
                          bracketsCrudRepository: BracketsCrudRepository,
                          dashboardStateCrudRepository: DashboardStateCrudRepository,
                          competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository) =
            StateQueryService(clusterSession, restTemplate,
                    competitionStateCrudRepository, competitionPropertiesCrudRepository, scheduleCrudRepository,
                    categoryStateCrudRepository, competitorCrudRepository,
                    dashboardStateCrudRepository,
                    bracketsCrudRepository)
}