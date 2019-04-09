package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.serde.ClusterInfoSerializer
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.repository.CompetitionStateRepository
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.transport.Address
import io.scalecube.transport.Message
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.web.ServerProperties
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

data class MemberWithRestPort(val member: Member, val restPort: Int) {
    fun restAddress(): Address = Address.create(member.address().host(), restPort)
}

class ClusterSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                     private val cluster: Cluster,
                     adminClient: KafkaAdminUtils,
                     private val kafkaProperties: KafkaProperties,
                     private val serverProperties: ServerProperties,
                     private val competitionStateRepository: CompetitionStateRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterSession::class.java)
        const val COMPETITION_LEADER_KEY = "compservice-leader"
        const val COMPETITION_PROCESSING_STARTED = "competitionProcessing"
        const val COMPETITION_PROCESSING_STOPPED = "competitionProcessingStopped"
        const val REST_PORT_METADATA_KEY = "rest_port"
        const val TYPE = "type"
    }

    private val leaderChangelogTopic = adminClient.createTopicIfMissing(
            kafkaProperties.leaderChangelogTopic,
            kafkaProperties.defaultTopicOptions.partitions,
            kafkaProperties.defaultTopicOptions.replicationFactor,
            compacted = true)

    private val producer: KafkaProducer<String, ClusterInfo>

    init {
        val props = Properties().apply { putAll(kafkaProperties.producer.properties) }
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ClusterInfoSerializer::class.java.canonicalName
        producer = KafkaProducer(props)
        producer.send(ProducerRecord(leaderChangelogTopic, COMPETITION_LEADER_KEY,
                ClusterInfo()
                        .setClusterMembers(cluster.members()
                                ?.map { member ->
                                    ClusterMember()
                                            .setId(member.id())
                                            .setUri(getUrlPrefix(member.address().host(), serverProperties.port))
                                            .setHost(member.address().host())
                                            .setPort(serverProperties.port.toString())
                                }?.toTypedArray())))
        producer.flush()
    }

    fun isProcessedLocally(competitionId: String): Boolean {
        return localCompetitionIds.contains(competitionId) || cluster.member().id() == clusterMembers[competitionId]?.member?.id()
    }

    fun getUrlPrefix(host: String, port: Int) = "http://$host:$port"

    fun findProcessingMember(competitionId: String): Address? = run {
        log.info("Getting info about instances processing competition $competitionId")
        clusterMembers[competitionId]?.restAddress()
    } ?: run {
        if (localCompetitionIds.contains(competitionId)) {
            Address.create(cluster.member().address().host(), serverProperties.port)
        } else {
            null
        }
    } ?: run {
        if (competitionStateRepository.existsById(competitionId)) {
            broadcastCompetitionProcessingInfo(setOf(competitionId))
            Address.create(cluster.member().address().host(), serverProperties.port)
        } else {
            null
        }
    } ?: run {
        log.info("Did not find processing instance for $competitionId")
        null
    }

    private val clusterMembers = ConcurrentHashMap<String, MemberWithRestPort>()
    private val localCompetitionIds = ConcurrentSkipListSet<String>()


    fun broadcastCompetitionProcessingInfo(competitionIds: Set<String>) {
        if (competitionIds.fold(false) { acc, s -> (acc || localCompetitionIds.add(s)) }) {
            cluster.spreadGossip(Message.withData(CompetitionProcessingMessage(MemberWithRestPort(cluster.member(), serverProperties.port), CompetitionProcessingInfo(cluster.member(), competitionIds)))
                    .headers(mapOf(TYPE to COMPETITION_PROCESSING_STARTED)).build()).subscribe()
        }
    }

    fun broadcastCompetitionProcessingStopped(competitionIds: Set<String>) {
        localCompetitionIds.removeAll(competitionIds)
        cluster.spreadGossip(Message.withData(CompetitionProcessingMessage(MemberWithRestPort(cluster.member(), serverProperties.port), CompetitionProcessingInfo(cluster.member(), competitionIds)))
                .headers(mapOf(TYPE to COMPETITION_PROCESSING_STOPPED)).build()).subscribe()
    }

    fun isLocal(address: Address) = (cluster.address().host() == address.host() || address.host() == clusterConfigurationProperties.advertisedHost) && address.port() == serverProperties.port

    fun init() {
        cluster.listenMembership().subscribe {
            when (it.type()) {
                MembershipEvent.Type.ADDED -> {
                    log.info("Member added to the cluster: ${it.member()}")
                }
                MembershipEvent.Type.REMOVED -> {
                    log.info("Member removed from the cluster: ${it.member()}")
                    if (it.oldMetadata()[REST_PORT_METADATA_KEY] != null) {
                        val m = MemberWithRestPort(it.member(), it.oldMetadata()[REST_PORT_METADATA_KEY]?.toInt()!!)
                        val keysToRemove = clusterMembers.filter { entry -> entry.value == m }.keys
                        keysToRemove.forEach { id -> clusterMembers.remove(id) }
                    }
                }
                MembershipEvent.Type.UPDATED -> {
                    log.info("Cluster member updated: ${it.member()}")
                    val om = MemberWithRestPort(it.member(), it.oldMetadata()[REST_PORT_METADATA_KEY]?.toInt()!!)
                    val nm = MemberWithRestPort(it.member(), it.newMetadata()[REST_PORT_METADATA_KEY]?.toInt()!!)
                    val keysToUpdate = clusterMembers.filter { entry -> entry.value == om }.keys
                    keysToUpdate.forEach { id -> clusterMembers[id] = nm }
                }
                else -> {
                    log.info("Strange membership event: $it")
                }
            }
            producer.send(ProducerRecord(leaderChangelogTopic, COMPETITION_LEADER_KEY,
                    ClusterInfo()
                            .setClusterMembers(cluster.members()
                                    ?.map { member ->
                                        ClusterMember()
                                                .setId(member.id())
                                                .setUri(getUrlPrefix(member.address().host(), serverProperties.port))
                                                .setHost(member.address().host())
                                                .setPort(serverProperties.port.toString())
                                    }?.toTypedArray())))
            producer.flush()
        }
        cluster.listenGossips().subscribe {
            try {
                if (it.header(TYPE) == COMPETITION_PROCESSING_STARTED) {
                    val msgData = it.data<CompetitionProcessingMessage>()
                    msgData?.info?.competitionIds?.forEach { s -> clusterMembers[s] = msgData.memberWithRestPort }
                }
                if (it.header(TYPE) == COMPETITION_PROCESSING_STOPPED) {
                    val msgData = it.data<CompetitionProcessingMessage>()
                    msgData?.info?.competitionIds?.forEach { s ->
                        if (clusterMembers[s] == msgData.memberWithRestPort) {
                            clusterMembers.remove(s)
                        }
                        competitionStateRepository.delete(s)
                    }
                }
            } catch (e: Exception) {
                log.warn("Error when processing a gossip.", e)
            }
        }
    }

    fun stop() {
        producer.close()
    }

    fun localMemberId(): String {
        return cluster.member().id()
    }
}