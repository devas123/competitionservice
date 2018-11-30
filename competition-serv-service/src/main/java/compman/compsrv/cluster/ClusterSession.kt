package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.cluster.CompetitionProcessingInfo
import compman.compsrv.model.cluster.CompetitionProcessingMessage
import compman.compsrv.model.cluster.CompetitionStateSnapshotMessage
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.repository.CategoryCrudRepository
import compman.compsrv.repository.CompetitionStateCrudRepository
import io.scalecube.cluster.Cluster
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.transport.Message
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import reactor.core.Disposable
import java.util.concurrent.ConcurrentHashMap

class ClusterSession(clusterConfigurationProperties: ClusterConfigurationProperties,
                     private val restTemplate: RestTemplate,
                     private val cluster: Cluster,
                     private val adminClient: KafkaAdminUtils,
                     private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                     private val categoryStateCrudRepository: CategoryCrudRepository,
                     private val kafkaProperties: KafkaProperties) {

    companion object {
        private val log = LoggerFactory.getLogger(ClusterSession::class.java)
        const val COMPETITION_LEADER_KEY = "compservice-leader"
        const val COMPETITION_SNAPSHOT = "competitionSnapshot"
        const val COMPETITION_PROCESSING = "competitionProcessing"
        const val TYPE = "type"


    }

    private fun getUrlPrefix(host: String, port: Int) = "http://$host:$port"

    private fun findProcessingMember(competitionId: String) = clusterMembers.entries.find { it.value.competitionIds.contains(competitionId) }?.key

    private val listenerString = getUrlPrefix(clusterConfigurationProperties.advertisedHost, clusterConfigurationProperties.advertisedPort)

    private val clusterMembers = ConcurrentHashMap<Member, CompetitionProcessingInfo>()


    fun broadcastCompetitionProcessingInfo(competitionIds: Set<String>): Disposable =
            cluster.spreadGossip(Message.withData(CompetitionProcessingMessage(cluster.member(), CompetitionProcessingInfo(cluster.member(), competitionIds)))
                    .header(TYPE, COMPETITION_PROCESSING).build()).subscribe()

    fun broadcastCompetitionStateSnapshot(competitionStateSnapshot: CompetitionStateSnapshot): Disposable {
        return cluster.spreadGossip(Message.withData(CompetitionStateSnapshotMessage(cluster.member(), competitionStateSnapshot))
                .header(TYPE, COMPETITION_SNAPSHOT).build()).subscribe()
    }

    fun getCategoryState(categoryId: String) = categoryStateCrudRepository.findById(categoryId)


    fun getCompetitionProperties(competitionId: String): CompetitionState? = competitionStateCrudRepository.findById(competitionId).orElseGet {
        val address = findProcessingMember(competitionId)?.address()
        address?.let { restTemplate.getForObject("${getUrlPrefix(it.host(), it.port())}/api/v1/store/competitionstate?competitionId=$competitionId&internal=true", CompetitionState::class.java) }
    }

    fun init() {
        val leaderChangelogTopic = adminClient.createTopicIfMissing(
                kafkaProperties.leaderChangelogTopic,
                kafkaProperties.defaultTopicOptions.partitions,
                kafkaProperties.defaultTopicOptions.replicationFactor,
                compacted = true)

        val producer = KafkaProducer<String, String>(kafkaProperties.producer.properties)
        producer.send(ProducerRecord(leaderChangelogTopic, COMPETITION_LEADER_KEY, listenerString))
        producer.flush()
        cluster.listenMembership().subscribe {
            when (it.type()) {
                MembershipEvent.Type.ADDED -> {
                    log.info("Member added to the cluster: ${it.member()}")
                    clusterMembers.putIfAbsent(it.member(), CompetitionProcessingInfo(it.member(), emptySet()))
                }
                MembershipEvent.Type.REMOVED -> {
                    log.info("Member removed from the cluster: ${it.member()}")
                    clusterMembers.remove(it.member())
                }
                MembershipEvent.Type.UPDATED -> {
                    log.info("Cluster member updated: ${it.oldMember()} -> ${it.newMember()}")
                    val competitions = clusterMembers.remove(it.oldMember())
                    clusterMembers[it.newMember()] = competitions ?: CompetitionProcessingInfo(it.newMember(), emptySet())
                }
                else -> {
                    log.info("Strange membership event: $it")
                }
            }
        }
        cluster.listenGossips().subscribe {
            if (it.header(TYPE) == COMPETITION_PROCESSING) {
                val msgData = it.data<CompetitionProcessingMessage>()
                if (msgData != null) {
                    clusterMembers.merge(msgData.member, msgData.info) { t: CompetitionProcessingInfo, u: CompetitionProcessingInfo ->
                        t.addCompetitionIds(u.competitionIds)
                    }
                }
            }
            if (it.header(TYPE) == COMPETITION_SNAPSHOT) {

            }
        }
    }
}