package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

data class MemberWithRestPort(val id: String, val host: String, val port: Int, val restPort: Int): Serializable {
    constructor(member: Member, restPort: Int): this(member.id(), member.address().host(), member.address().port(), restPort)
    fun restAddress(): Address = Address.create(host, restPort)
}

class ClusterSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                     private val cluster: Cluster,
                     adminClient: KafkaAdminUtils,
                     private val kafkaProperties: KafkaProperties,
                     private val serverProperties: ServerProperties,
                     private val mapper: ObjectMapper,
                     private val competitionStateRepository: CompetitionStateRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterSession::class.java)
        const val COMPETITION_LEADER_KEY = "compservice-leader"
        const val COMPETITION_PROCESSING_STARTED = "competitionProcessing"
        const val COMPETITION_PROCESSING_STOPPED = "competitionProcessingStopped"
        const val REST_PORT_METADATA_KEY = "rest_port"
        const val MEMBER_HOSTNAME_METADATA_KEY = "member_hostname"
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
                                    val hostName = cluster.metadata(member)?.get(MEMBER_HOSTNAME_METADATA_KEY)
                                            ?: member.address().host()
                                    ClusterMember()
                                            .setId(member.id())
                                            .setUri(getUrlPrefix(hostName, serverProperties.port))
                                            .setHost(hostName)
                                            .setPort(serverProperties.port.toString())
                                }?.toTypedArray())))
        producer.flush()
        cluster.listenGossips().subscribe{
            log.info("Received a gossip: $it")
            processMessage(it)
        }
    }

    private fun processMessage(it: Message?) {
        try {
            if (it?.header(TYPE) == COMPETITION_PROCESSING_STARTED) {
                val msgData = mapper.readValue(it.data<String>(), CompetitionProcessingMessage::class.java)
                log.info("Received competition processing started message. $msgData")
                msgData?.info?.competitionIds?.forEach { s -> clusterMembers[s] = msgData.memberWithRestPort }
            }
            if (it?.header(TYPE) == COMPETITION_PROCESSING_STOPPED) {
                val msgData = mapper.readValue(it.data<String>(), CompetitionProcessingMessage::class.java)
                log.info("Received competition processing stopped message. $msgData")
                msgData?.info?.competitionIds?.forEach { s ->
                    if (clusterMembers[s] == msgData.memberWithRestPort) {
                        clusterMembers.remove(s)
                    }
                    competitionStateRepository.delete(s)
                }
            }
        } catch (e: Exception) {
            log.warn("Error when processing a message.", e)
        }
    }

    fun isProcessedLocally(competitionId: String): Boolean {
        return localCompetitionIds.contains(competitionId) || cluster.member().id() == clusterMembers[competitionId]?.id
    }

    fun getUrlPrefix(host: String, port: Int) = "http://$host:$port${serverProperties.servlet.contextPath}"

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
            val member = MemberWithRestPort(cluster.member(), serverProperties.port)
            val data = mapper.writeValueAsString(CompetitionProcessingMessage(member, CompetitionProcessingInfo(member, competitionIds)))
            val message = Message.withData(data)
                    .header(TYPE, COMPETITION_PROCESSING_STARTED)
                    .sender(cluster.member().address())
                    .build()
            cluster.spreadGossip(message).subscribe {
                log.info("Broadcasting the following competition processing info: ${cluster.member()} -> $competitionIds")
            }
        }
    }

    fun broadcastCompetitionProcessingStopped(competitionIds: Set<String>) {
        localCompetitionIds.removeAll(competitionIds)
        val member = MemberWithRestPort(cluster.member(), serverProperties.port)
        val data = mapper.writeValueAsString(CompetitionProcessingMessage(member, CompetitionProcessingInfo(member, competitionIds)))
        val message = Message.withData(data)
                .header(TYPE, COMPETITION_PROCESSING_STOPPED)
                .sender(cluster.member().address())
                .build()
        cluster.members().forEach {
            cluster.send(it, message)
        }
        cluster.spreadGossip(message).subscribe {
            log.info("Broadcasting the following competition stopped processing info: ${cluster.member()} -> $competitionIds")
        }
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
                    if (it.oldMetadata()?.get(REST_PORT_METADATA_KEY) != null) {
                        val m = MemberWithRestPort(it.member(), it.oldMetadata()?.get(REST_PORT_METADATA_KEY)?.toInt()!!)
                        val keysToRemove = clusterMembers.filter { entry -> entry.value == m }.keys
                        keysToRemove.forEach { id -> clusterMembers.remove(id) }
                    }
                }
                MembershipEvent.Type.UPDATED -> {
                    log.info("Cluster member updated: ${it.member()}")
                    val om = MemberWithRestPort(it.member(), it.oldMetadata()?.get(REST_PORT_METADATA_KEY)?.toInt()!!)
                    val nm = MemberWithRestPort(it.member(), it.newMetadata()?.get(REST_PORT_METADATA_KEY)?.toInt()!!)
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
                                        val hostName = cluster.metadata(member)?.get(MEMBER_HOSTNAME_METADATA_KEY)
                                                ?: member.address().host()
                                        val port = cluster.metadata(member)?.get(REST_PORT_METADATA_KEY)
                                                ?: member.address().port().toString()
                                        ClusterMember()
                                                .setId(member.id())
                                                .setUri(getUrlPrefix(hostName, port.toInt()))
                                                .setHost(hostName)
                                                .setPort(port)
                                    }?.toTypedArray())))
            producer.flush()
        }
    }

    fun stop() {
        producer.close()
    }

    fun localMemberId(): String {
        return cluster.member().id()
    }
}