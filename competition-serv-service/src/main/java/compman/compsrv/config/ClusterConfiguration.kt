package compman.compsrv.config

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.cluster.MemberMetadata
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.service.ClusterInfoService
import compman.compsrv.service.CommandCache
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.ClusterConfig
import io.scalecube.cluster.Member
import io.scalecube.cluster.metadata.MetadataCodec
import io.scalecube.net.Address
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.kafka.core.KafkaTemplate
import java.net.InetAddress
import java.nio.ByteBuffer

@Configuration
@EnableConfigurationProperties(ClusterConfigurationProperties::class)
class ClusterConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterConfiguration::class.java)
        fun getClusterMetadataForMember(cluster: Cluster, member: Member): MemberMetadata? {
            return cluster.metadata<MemberMetadata>(member).orElse(null)
        }
    }

    @Bean
    fun cluster(clusterConfigurationProperties: ClusterConfigurationProperties,
                serverProperties: ServerProperties,
                objectMapper: ObjectMapper): ClusterConfig {
        val memberHost = if (clusterConfigurationProperties.advertisedHost.isNullOrBlank()) {
            InetAddress.getLocalHost().hostAddress
        } else {
            clusterConfigurationProperties.advertisedHost
        }
        val clusterSeed = clusterConfigurationProperties.clusterSeed?.mapNotNull { s -> Address.from(s) } ?: emptyList()
        log.info("Configured initial cluster seed: $clusterSeed")
        return ClusterConfig.defaultConfig()
                .transport { it
                        .host(InetAddress.getLocalHost().hostAddress)
                        .port(clusterConfigurationProperties.advertisedPort)
                }
                .membership { it.seedMembers(clusterSeed) }

                .metadata(MemberMetadata(serverProperties.port.toString(), memberHost))
                .metadataCodec(object: MetadataCodec {
                    override fun deserialize(buffer: ByteBuffer?): Any? {
                        return buffer?.let { objectMapper.readValue(it.array(), MemberMetadata::class.java) }
                    }
                    override fun serialize(metadata: Any?): ByteBuffer? {
                        return metadata?.let { ByteBuffer.wrap(objectMapper.writeValueAsBytes(it)) }
                    }
                })
    }

    @Bean(destroyMethod = "stop")
    @DependsOn("cluster", "leaderChangelogTopic", "kafkaAdmin")
    fun clusterSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                       cluster: ClusterConfig,
                       kafkaProperties: org.springframework.boot.autoconfigure.kafka.KafkaProperties,
                       serverProperties: ServerProperties,
                       objectMapper: ObjectMapper,
                       competitionStateRepository: CompetitionStateRepository,
                       commandCache: CommandCache,
                       kafkaTemplate: KafkaTemplate<String, CommandDTO>) =
            ClusterSession(clusterConfigurationProperties,
                    cluster,
                    kafkaProperties,
                    serverProperties, objectMapper, competitionStateRepository,
                    commandCache, kafkaTemplate)


    @Bean
    fun clusterInfoService(clusterSession: ClusterSession) = ClusterInfoService(clusterSession)
}