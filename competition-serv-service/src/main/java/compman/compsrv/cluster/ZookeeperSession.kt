package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.HostStoreInfo
import compman.compsrv.kafka.streams.JobStream
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.competition.MatState
import compman.compsrv.service.*
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.state.HostInfo
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import kotlin.concurrent.thread

class ZookeeperSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                       private val kafkaProperties: KafkaProperties,
                       private val categoryStateService: CategoryStateService,
                       private val competitionStateService: CompetitionPropertiesService,
                       private val dashboardStateService: DashboardStateService,
                       private val matStateService: MatStateService,
                       private val restTemplate: RestTemplate,
                       private val matCommandsValidatorRegistry: MatCommandsValidatorRegistry,
                       private val leaderElection: LeaderElection) : LeadershipListener {
    private lateinit var workerProcess: WorkerProcess
    private lateinit var leaderProcess: LeaderProcess
    lateinit var stateQueryService: StateQueryService
        private set
    private val listenerStr = "http://${clusterConfigurationProperties.advertisedHost}:${clusterConfigurationProperties.advertisedPort}"

    override fun onGranted() {
        if (!::leaderProcess.isInitialized) {
            leaderProcess = LeaderProcess(listenerStr, kafkaProperties, stateQueryService, competitionStateService, dashboardStateService)
            leaderProcess.start()
        }
    }

    override fun onRevoked() {
        if (::leaderProcess.isInitialized) {
            leaderProcess.stop()
        }
    }



    fun getHostForCategory(categoryId: String): HostStoreInfo {
        val metadataService = workerProcess.getMetadataService()
        return run {
            log.info("Getting host info for category $categoryId")
            //if this instance processes this competition
            metadataService.streamsMetadataForStoreAndKey(
                    JobStream.CATEGORY_STATE_STORE_NAME,
                    categoryId,
                    Serdes.String().serializer())
        }
    }

    fun getHostForMat(matId: String): HostStoreInfo {
        val metadataService = workerProcess.getMetadataService()
        return run {
            log.info("Getting host info for matId $matId")
            //if this instance processes this competition
            metadataService.streamsMetadataForStoreAndKey(
                    JobStream.MAT_STATE_STORE_NAME,
                    matId,
                    Serdes.String().serializer())
        }
    }

    fun getCategoryState(categoryId: String): CategoryState? {
        return workerProcess.getCategoryState(categoryId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZookeeperSession::class.java)
    }

    fun getCompetitionProperties(competitionId: String): CompetitionProperties? =
            if (leaderElection.isConnected()) {
                leaderProcess.getCompetitionProperties(competitionId)
            } else {
                throw IllegalStateException("I am not the leader (${leaderElection.isLeader()}) or i am not connected (${leaderElection.isConnected()}).")
            }

    private fun init() {
        workerProcess = WorkerProcess(
                kafkaProperties,
                categoryStateService,
                matStateService,
                HostInfo(clusterConfigurationProperties.advertisedHost, clusterConfigurationProperties.advertisedPort),
                matCommandsValidatorRegistry)
        workerProcess.start()
        Runtime.getRuntime().addShutdownHook(thread(start = false) { close() })
        stateQueryService = StateQueryService(this, restTemplate, clusterConfigurationProperties)
        leaderElection.registerListener(this)
    }

    init {
        init()
    }

    fun close() {
        try {
            workerProcess.stop()
            if (leaderElection.isLeader()) {
                leaderProcess.stop()
            }
        } catch (e: Exception) {
            log.warn("Exception while closing zookeeper session", e)
        }
    }

    fun getCategoriesForCompetition(competitionId: String) = if (leaderElection.isLeader() && ::leaderProcess.isInitialized) {
        leaderProcess.getCategories(competitionId)?.toTypedArray()
    } else {
        throw RuntimeException("The node you are asking is not a leader node, or it is not initialized. Please try again later.")
    }

    fun getCompetitions(status: CompetitionStatus?, creatorId: String?): Array<CompetitionProperties> {
        if (leaderElection.isLeader() && ::leaderProcess.isInitialized) {
            return leaderProcess.getCompetitions(status, creatorId)
        } else {
            throw RuntimeException("The node you are asking is not a leader node, or it is not initialized. Please try again later.")
        }
    }

    fun getDashboardState(competitionId: String) = if (leaderElection.isLeader() && leaderElection.isConnected()) {
        leaderProcess.getDashboardState(competitionId)
    } else {
        throw IllegalStateException("I am not the leader (${leaderElection.isLeader()}) or i am not connected (${leaderElection.isConnected()}).")
    }

    fun getMatState(matId: String): MatState? {
        return workerProcess.getMatState(matId)
    }
}