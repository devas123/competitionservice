package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory
import compman.compsrv.kafka.streams.MetadataService
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.cluster.CompetitionProcessingInfo
import compman.compsrv.model.cluster.CompetitionProcessingMessage
import compman.compsrv.model.cluster.CompetitionStateSnapshotMessage
import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.repository.CompetitionStateSnapshotCrudRepository
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.transport.Address
import io.scalecube.transport.Message
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.web.ServerProperties
import reactor.core.Disposable
import java.util.concurrent.ConcurrentHashMap
import javax.ws.rs.NotFoundException

data class MemberWithRestPort(val member: Member, val restPort: Int) {
    fun restAddress(): Address = Address.create(member.address().host(), restPort)
}

class ClusterSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                     private val cluster: Cluster,
                     private val adminClient: KafkaAdminUtils,
                     private val competitionStateSnapshotCrudRepository: CompetitionStateSnapshotCrudRepository,
                     private val kafkaProperties: KafkaProperties,
                     private val streamsMetadataService: MetadataService,
                     private val serverProperties: ServerProperties) {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterSession::class.java)
        const val COMPETITION_LEADER_KEY = "compservice-leader"
        const val COMPETITION_SNAPSHOT = "competitionSnapshot"
        const val COMPETITION_PROCESSING = "competitionProcessing"
        const val REST_PORT_METADATA_KEY = "rest_port"
        const val TYPE = "type"
    }

    fun getUrlPrefix(host: String, port: Int) = "http://$host:$port"

    fun findProcessingMember(competitionId: String): Address? = run {
        log.info("Getting info about instances processing competition $competitionId")
        clusterMembers[competitionId]?.restAddress()
    } ?: try {
        log.info("Did not find in cluster info, looking in the kafka streams info for competition id $competitionId")
        val metadata = streamsMetadataService.streamsMetadataForStoreAndKey(CompetitionProcessingStreamsBuilderFactory.COMPETITION_STATE_SNAPSHOT_STORE_NAME, competitionId, StringSerializer())
        log.info("Found metadata for $competitionId: $metadata")
        Address.create(metadata.host, metadata.port)
    } catch (e: NotFoundException) {
        log.info("Did not find processing instance for $competitionId")
        null
    }

    private val restListenerString = getUrlPrefix(clusterConfigurationProperties.advertisedHost, serverProperties.port)

    private val clusterMembers = ConcurrentHashMap<String, MemberWithRestPort>()


    fun broadcastCompetitionProcessingInfo(competitionIds: Set<String>): Disposable =
            cluster.spreadGossip(Message.withData(CompetitionProcessingMessage(MemberWithRestPort(cluster.member(), serverProperties.port), CompetitionProcessingInfo(cluster.member(), competitionIds)))
                    .headers(mapOf(TYPE to COMPETITION_PROCESSING)).build()).subscribe()

    fun broadcastCompetitionStateSnapshot(competitionStateSnapshot: CompetitionStateSnapshot): Disposable {
        return cluster.spreadGossip(Message.withData(CompetitionStateSnapshotMessage(cluster.member(), competitionStateSnapshot))
                .header(TYPE, COMPETITION_SNAPSHOT).build()).subscribe()
    }

    fun isLocal(address: Address) = cluster.address() == address || (address.host() == clusterConfigurationProperties.advertisedHost && address.port() == clusterConfigurationProperties.advertisedPort)

    fun init() {
        val leaderChangelogTopic = adminClient.createTopicIfMissing(
                kafkaProperties.leaderChangelogTopic,
                kafkaProperties.defaultTopicOptions.partitions,
                kafkaProperties.defaultTopicOptions.replicationFactor,
                compacted = true)

        val producer = KafkaProducer<String, String>(kafkaProperties.producer.properties)
        producer.send(ProducerRecord(leaderChangelogTopic, COMPETITION_LEADER_KEY, restListenerString))
        producer.flush()
        producer.close()
        cluster.listenMembership().subscribe {
            when (it.type()) {
                MembershipEvent.Type.ADDED -> {
                    log.info("Member added to the cluster: ${it.member()}")
                }
                MembershipEvent.Type.REMOVED -> {
                    log.info("Member removed from the cluster: ${it.member()}")
                    if (it.member().metadata()[REST_PORT_METADATA_KEY] != null) {
                        val m = MemberWithRestPort(it.member(), it.member().metadata()[REST_PORT_METADATA_KEY]?.toInt()!!)
                        val keysToRemove = clusterMembers.filter { entry -> entry.value == m }.keys
                        keysToRemove.forEach { id -> clusterMembers.remove(id) }
                    }
                }
                MembershipEvent.Type.UPDATED -> {
                    log.info("Cluster member updated: ${it.oldMember()} -> ${it.newMember()}")
                    val om = MemberWithRestPort(it.oldMember(), it.oldMember().metadata()[REST_PORT_METADATA_KEY]?.toInt()!!)
                    val nm = MemberWithRestPort(it.newMember(), it.newMember().metadata()[REST_PORT_METADATA_KEY]?.toInt()!!)
                    val keysToUpdate = clusterMembers.filter { entry -> entry.value == om }.keys
                    keysToUpdate.forEach { id -> clusterMembers[id] = nm }
                }
                else -> {
                    log.info("Strange membership event: $it")
                }
            }
        }
        cluster.listenGossips().subscribe {
            try {
                if (it.header(TYPE) == COMPETITION_PROCESSING) {
                    val msgData = it.data<CompetitionProcessingMessage>()
                    msgData?.info?.competitionIds?.forEach { s -> clusterMembers[s] = msgData.member }
                }
                if (it.header(TYPE) == COMPETITION_SNAPSHOT) {
                    val msgData = it.data<CompetitionStateSnapshot>()
                    msgData?.let(competitionStateSnapshotCrudRepository::save)
                }
            } catch (e: Exception) {
                log.warn("Error when processing a gossip.", e)
            }
        }
    }
}