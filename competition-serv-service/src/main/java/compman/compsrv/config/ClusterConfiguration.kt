package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.json.ObjectMapperFactory
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
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestTemplate
import kotlin.concurrent.thread

@Configuration
@EnableConfigurationProperties(ClusterConfigurationProperties::class)
class ClusterConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterConfiguration::class.java)
    }

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = ObjectMapperFactory.createObjectMapper()


    @Bean(destroyMethod = "shutdown")
    fun cluster(clusterConfigurationProperties: ClusterConfigurationProperties, serverProperties: ServerProperties, objectMapper: ObjectMapper): Cluster {
        val memberHost = if (clusterConfigurationProperties.advertisedHost.isNullOrBlank()) {
            ClusterConfig.DEFAULT_MEMBER_HOST
        } else {
            clusterConfigurationProperties.advertisedHost
        }
        val codec = ClusterMessageCodec(objectMapper)
        val clusterSeed = clusterConfigurationProperties.clusterSeed?.mapNotNull { s -> Address.from(s) } ?: emptyList()
        val clusterConfig = ClusterConfig.builder()
                .transportConfig(TransportConfig.builder()
                        .port(clusterConfigurationProperties.advertisedPort).build())
                .seedMembers(clusterSeed)
                .messageCodec(codec)
                .addMetadata(ClusterSession.REST_PORT_METADATA_KEY, serverProperties.port.toString())
                .addMetadata(ClusterSession.MEMBER_HOSTNAME_METADATA_KEY, memberHost)
                .build()
        val cluster = Cluster.joinAwait(clusterConfig)
        Runtime.getRuntime().addShutdownHook(thread(start = false) { cluster.shutdown() })
        log.info("Started instance at ${cluster.address().host()}:${cluster.address().port()} with rest port: ${serverProperties.port}")
        log.info("Members of the cluster: ")
        cluster.members()?.forEach {
            log.info("${it.id()} -> ${cluster.metadata(it)?.get(ClusterSession.MEMBER_HOSTNAME_METADATA_KEY)}, ${it.address()}, ${cluster.metadata(it)?.get(ClusterSession.REST_PORT_METADATA_KEY)}")
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
                       objectMapper: ObjectMapper,
                       competitionStateRepository: CompetitionStateRepository) =
            ClusterSession(clusterConfigurationProperties,
                    cluster,
                    adminClient, kafkaProperties, serverProperties, objectMapper, competitionStateRepository)

    @Bean
    fun stateQueryService(restTemplate: RestTemplate,
                          clusterSession: ClusterSession,
                          categoryStateCrudRepository: CategoryStateCrudRepository,
                          competitionStateCrudRepository: CompetitionStateCrudRepository,
                          scheduleCrudRepository: ScheduleCrudRepository,
                          competitorCrudRepository: CompetitorCrudRepository,
                          bracketsCrudRepository: BracketsCrudRepository,
                          dashboardStateCrudRepository: DashboardStateCrudRepository,
                          fightCrudRepository: FightCrudRepository,
                          dashboardPeriodCrudRepository: DashboardPeriodCrudRepository,
                          competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository) =
            StateQueryService(clusterSession, restTemplate,
                    competitionStateCrudRepository, competitionPropertiesCrudRepository, scheduleCrudRepository,
                    fightCrudRepository,
                    categoryStateCrudRepository,
                    competitorCrudRepository,
                    dashboardStateCrudRepository,
                    dashboardPeriodCrudRepository,
                    bracketsCrudRepository)
}