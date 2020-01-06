package compman.compsrv.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.serde.ClusterInfoSerializer
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionInfoPayload
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.service.CommandCache
import compman.compsrv.service.CommandProducer
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.transport.Address
import io.scalecube.transport.Message
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

class ClusterSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                     private val cluster: Cluster,
                     kafkaProperties: KafkaProperties,
                     private val serverProperties: ServerProperties,
                     private val mapper: ObjectMapper,
                     private val competitionStateRepository: CompetitionStateRepository,
                     private val commandCache: CommandCache,
                     private val kafkaTemplate: KafkaTemplate<String, CommandDTO>) {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterSession::class.java)
        const val COMPETITION_LEADER_KEY = "compservice-leader"
        const val COMPETITION_PROCESSING_STARTED = "competitionProcessingStarted"
        const val COMPETITION_PROCESSING_INFO = "competitionProcessingInfo"
        const val COMPETITION_PROCESSING_STOPPED = "competitionProcessingStopped"
        const val REST_PORT_METADATA_KEY = "rest_port"
        const val MEMBER_HOSTNAME_METADATA_KEY = "member_hostname"
        const val TYPE = "type"
    }

    private val leaderChangelogTopic = CompetitionServiceTopics.LEADER_CHANGELOG_TOPIC
    private val producer: KafkaProducer<String, ClusterInfo>

    init {
        val props = kafkaProperties.buildProducerProperties()
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ClusterInfoSerializer::class.java
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        producer = KafkaProducer(props)
    }

    fun createProcessingInfoEvents(correlationId: String, competitionIds: Set<String>): Array<EventDTO> {
        val member = MemberWithRestPort(cluster.member(), serverProperties.port)
        return competitionIds.map {
            EventDTO(null, correlationId, it, null, null, EventType.INTERNAL_COMPETITION_INFO,
                    mapper.writeValueAsString(CompetitionInfoPayload().setHost(member.host).setPort(member.restPort)
                            .setCompetitionId(it).setMemberId(member.id)),
                    emptyMap())
        }.toTypedArray()
    }

    private fun processMessage(it: Message?) {
        try {
            if (it?.header(TYPE) == COMPETITION_PROCESSING_STARTED || it?.header(TYPE) == COMPETITION_PROCESSING_INFO) {
                val msgData = mapper.readValue(it.data<String>(), CompetitionProcessingMessage::class.java)
                log.info("Received competition processing started message. $msgData")
                msgData?.info?.competitionIds?.forEach { s ->
                    if (!isLocal(it.sender())) {
                        localCompetitionIds.remove(s)
                    }
                    clusterMembers[s] = msgData.memberWithRestPort
                }
                if (!msgData.correlationId.isNullOrBlank()) {
                    commandCache.commandCallback(msgData.correlationId, createProcessingInfoEvents(msgData.correlationId, msgData.info.competitionIds))
                }
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
        log.info("Did not find processing instance for $competitionId, sending command to find the instance.")
        val correlationId = UUID.randomUUID().toString()
        val future = CompletableFuture<Array<EventDTO>>()
        commandCache.executeCommand(correlationId, future) {
            kafkaTemplate.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, competitionId,
                    CommandProducer.createSendProcessingInfoCommand(competitionId, correlationId)))
        }
        kotlin.runCatching { commandCache.waitForResult(future, Duration.ofSeconds(30)) }.recover { e ->
            log.error("Error while executing competition info command", e)
            null
        }.map { arrayOfEventDTOs ->
            arrayOfEventDTOs?.find { e -> e.type == EventType.INTERNAL_COMPETITION_INFO }?.let {
                val payload = mapper.readValue(it.payload, CompetitionInfoPayload::class.java)
                Address.create(payload.host, payload.port)
            }
        }.getOrNull()
    }

    private val clusterMembers = ConcurrentHashMap<String, MemberWithRestPort>()
    private val localCompetitionIds = ConcurrentSkipListSet<String>()


    fun broadcastCompetitionProcessingInfo(competitionIds: Set<String>, correlationId: String? = null) {
        log.info("Broadcast competition processing info method call: $competitionIds, $correlationId")
        if (!correlationId.isNullOrBlank()) {
            val member = MemberWithRestPort(cluster.member(), serverProperties.port)
            val data = CompetitionProcessingMessage(correlationId, member, CompetitionProcessingInfo(member, competitionIds))
            data.correlationId?.let {
                val events = createProcessingInfoEvents(correlationId, competitionIds)
                log.info("Executing command callback, correlation ID: $it, data: $events")
                commandCache.commandCallback(it, events)
            }
        }
        if (competitionIds.fold(false) { acc, s -> (acc || localCompetitionIds.add(s)) }) {
            val member = MemberWithRestPort(cluster.member(), serverProperties.port)
            val data = CompetitionProcessingMessage(correlationId, member, CompetitionProcessingInfo(member, competitionIds))
            val message = Message.withData(mapper.writeValueAsString(data))
                    .header(TYPE, COMPETITION_PROCESSING_STARTED)
                    .sender(cluster.member().address())
                    .build()
            cluster.spreadGossip(message).subscribe {
                log.info("Broadcasting the following competition processing info: ${cluster.member()} -> $data")
            }
        }
    }

    fun broadcastCompetitionProcessingStopped(competitionIds: Set<String>) {
        log.info("Broadcast competition processing stopped: $competitionIds")
        localCompetitionIds.removeAll(competitionIds)
        val member = MemberWithRestPort(cluster.member(), serverProperties.port)
        log.info("ClusterMembers now are: $clusterMembers")
        competitionIds.forEach {
            if (clusterMembers[it]?.id == cluster.member().id()) {
                clusterMembers.remove(it)
            }
        }
        log.info("Updated clusterMembers now are: $clusterMembers")
        val data = mapper.writeValueAsString(CompetitionProcessingMessage(member, CompetitionProcessingInfo(member, competitionIds)))
        val message = Message.withData(data)
                .header(TYPE, COMPETITION_PROCESSING_STOPPED)
                .sender(cluster.member().address())
                .build()
        cluster.spreadGossip(message).subscribe {
            log.info("Broadcasting the following competition stopped processing info: ${cluster.member()} -> $competitionIds")
        }
    }

    fun isLocal(address: Address) = (cluster.address().host() == address.host() || address.host() == clusterConfigurationProperties.advertisedHost) && address.port() == serverProperties.port

    fun init() {
        cluster.listenGossips().subscribe {
            log.info("Received a gossip: $it")
            processMessage(it)
        }

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

        cluster.listenMembership().subscribe {
            broadcastCompetitionProcessingInfo(this.localCompetitionIds)
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
                                    ?.mapNotNull { member -> createClusterMember(member) }?.toTypedArray())))
            producer.flush()
        }
    }

    private fun createClusterMember(member: Member): ClusterMember? {
        val hostName = cluster.metadata(member)?.get(MEMBER_HOSTNAME_METADATA_KEY)
                ?: member.address().host()
        val port = cluster.metadata(member)?.get(REST_PORT_METADATA_KEY)
                ?: member.address().port().toString()
        return ClusterMember()
                .setId(member.id())
                .setUri(getUrlPrefix(hostName, port.toInt()))
                .setHost(hostName)
                .setPort(port)
    }


    fun stop() {
        producer.close()
    }

    fun localMemberId(): String {
        return cluster.member().id()
    }

    fun getClusterMembers(): Array<ClusterMember> =
            cluster.members()?.mapNotNull { createClusterMember(it) }?.toTypedArray() ?: emptyArray()

}