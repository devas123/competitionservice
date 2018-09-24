package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.kafka.streams.LeaderProcessStreams
import compman.compsrv.kafka.streams.MetadataService
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.service.ScheduleService
import compman.compsrv.service.StateQueryService
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class LeaderProcess(private val zookeeperSession: ZookeeperSession,
                    private val prefix: String,
                    private val znode: String,
                    listenerString: String,
                    private val closingListener: ClosingListener,
                    scheduleService: ScheduleService,
                    kafkaProperties: KafkaProperties,
                    stateQueryService: StateQueryService) : Watcher {

    companion object {
        private val log = LoggerFactory.getLogger(LeaderProcess::class.java)
        const val COMPETITION_LEADER_KEY = "compservice-leader"
    }

    //    private val zk = zookeeperSession.zk
    private var dead = false
    private val adminClient: KafkaAdminUtils
    private val mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper()
    private val metadataService: MetadataService
    private val leaderProcessStreams: LeaderProcessStreams


    init {
        mapper.findAndRegisterModules()

        //Create admin client
        val adminProps = Properties()
        adminProps[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        adminClient = KafkaAdminUtils(adminProps)


        //Put this node as a leader to a leader changelog
        val leaderChangelogTopic = adminClient.createTopicIfMissing(
                kafkaProperties.leaderChangelogTopic,
                kafkaProperties.defaultTopicOptions.partitions,
                kafkaProperties.defaultTopicOptions.replicationFactor,
                compacted = true)

        val producer = KafkaProducer<String, String>(kafkaProperties.producer.properties)
        producer.send(ProducerRecord(leaderChangelogTopic, COMPETITION_LEADER_KEY, listenerString))
        producer.flush()

        //Stream

        leaderProcessStreams = LeaderProcessStreams(adminClient, scheduleService, stateQueryService, kafkaProperties)
        metadataService = leaderProcessStreams.metadataService

        Runtime.getRuntime().addShutdownHook(thread(start = false) { producer.close(10, TimeUnit.SECONDS) })
    }




    override fun process(event: WatchedEvent?) {
        if (!dead && zookeeperSession.zk.state == CuratorFrameworkState.STARTED) {
            event?.also {
                when (it.type) {
                    Watcher.Event.EventType.None ->
                        when (it.state) {
                            Watcher.Event.KeeperState.Expired -> {
                                dead = true
                                closingListener.workerClosing(KeeperException.Code.SESSIONEXPIRED)
                            }
                            else -> {
                                znode.let { _ ->
                                    zookeeperSession.zk.children.usingWatcher(this).forPath(prefix)
                                }
                            }
                        }
                    else -> {
                        log.info("Got event ${event.type}, skipping")
                        zookeeperSession.zk.children.usingWatcher(this).forPath(prefix)
                    }
                }
            }
        } else {
            log.warn("Got an event to process but either i am dead ($dead), or ZK session is not STARTED (${zookeeperSession.zk.state})")
        }
    }

    private fun readCompProperties(statuses: Array<CompetitionStatus>?) = leaderProcessStreams.readCompProperties(statuses)

    fun start() {
        if (!dead) {
            log.info("Starting the leader process.")
            zookeeperSession.zk.children.usingWatcher(this@LeaderProcess).forPath(prefix)
            leaderProcessStreams.start()
        } else {
            log.warn("Cannot start leader process, because it is dead. Please create a new one. ")
        }
    }

    fun stop() {
        try {
            leaderProcessStreams.stop()
        } finally {
            dead = true
        }
    }

    fun getCompetitionProperties(competitionId: String) = leaderProcessStreams.getCompetitionProperties(competitionId)
    fun getCompetitions(status: CompetitionStatus?, creatorId: String?): Array<CompetitionProperties> = readCompProperties(status?.let { arrayOf(it) }).filter { creatorId.isNullOrBlank() || it.creatorId == creatorId }.toTypedArray()
    fun getCategories(competitionId: String): List<Category>? = getCompetitionProperties(competitionId)?.categories?.toList()
    fun getDashboardState(competitionId: String) = leaderProcessStreams.getDashboardState(competitionId)
}

