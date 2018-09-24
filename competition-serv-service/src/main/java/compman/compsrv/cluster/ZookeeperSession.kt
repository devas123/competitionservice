package compman.compsrv.cluster

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.HostStoreInfo
import compman.compsrv.kafka.streams.JobStream
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.competition.MatState
import compman.compsrv.service.CategoryStateService
import compman.compsrv.service.ScheduleService
import compman.compsrv.service.StateQueryService
import compman.compsrv.validators.CategoryCommandsValidatorRegistry
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.state.ConnectionState
import org.apache.curator.framework.state.ConnectionStateListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.state.HostInfo
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class ZookeeperSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                       private val kafkaProperties: KafkaProperties,
                       private val competitionStateService: CategoryStateService,
                       private val scheduleService: ScheduleService,
                       private val restTemplate: RestTemplate,
                       private val validators: CategoryCommandsValidatorRegistry,
                       private val matCommandsValidatorRegistry: MatCommandsValidatorRegistry) : ClosingListener, Watcher, ConnectionStateListener {

    override fun stateChanged(client: CuratorFramework?, newState: ConnectionState?) {
        if (newState == ConnectionState.LOST) {
            client?.close()
            close()
        }
        connected = newState?.isConnected ?: false
    }

    private val stateLock = ReentrantLock()

    private var retriesLeft = 10

    @Volatile
    var leader = false
        private set
    @Volatile
    var connected = false
        private set
    private val connectString: String = clusterConfigurationProperties.zookeeper.connectionString
    private val sessionTimeout: Int = clusterConfigurationProperties.zookeeper.sessionTimeout
    private val electionPath = "${clusterConfigurationProperties.zookeeper.namespace}${clusterConfigurationProperties.zookeeper.electionPath}"
    private val listenerStr = "http://${clusterConfigurationProperties.advertisedHost}:${clusterConfigurationProperties.advertisedPort}"
    private val connectTimeout = clusterConfigurationProperties.zookeeper.connectTimeout
    lateinit var zk: CuratorFramework
        private set
    private lateinit var electionNode: String
    private lateinit var workerProcess: WorkerProcess
    private lateinit var leaderProcess: LeaderProcess
    lateinit var stateQueryService: StateQueryService
        private set


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

    override fun process(event: WatchedEvent?) {
        if (connected) {
            event?.also {
                if (it.type == Watcher.Event.EventType.NodeDeleted) {
                    log.info("My local leader node has been deleted. Checking if the leader has changed.")
                    doLeaderElection()
                } else {
                    log.info("Received event: ${it.type}. Resubscribing to ${it.path}.")
                    zk.checkExists().usingWatcher(this).forPath(it.path)
                }
            }
        }
    }

    override fun leaderClosing(rc: KeeperException.Code) {
        leaderProcess.stop()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZookeeperSession::class.java)
    }

    override fun workerClosing(rc: KeeperException.Code) {
        //Close everything and try to reconnect;
        if (arrayOf(KeeperException.Code.SESSIONEXPIRED, KeeperException.Code.CONNECTIONLOSS).contains(rc)) {
            stateLock.lock()
            try {
                zk.close()
                workerProcess.stop()
                reconnect()
            } finally {
                stateLock.unlock()
            }
        }
    }

    private fun reconnect() {
        if (retriesLeft-- > 0) {
            init()
        } else {
            log.warn("Too many retries, cannot reconnect...")
        }
    }

    fun getCompetitionProperties(competitionId: String): CompetitionProperties? =
            if (leader && connected) {
                leaderProcess.getCompetitionProperties(competitionId)
            } else {
                throw IllegalStateException("I am not the leader ($leader) or i am not connected ($connected).")
            }


    private fun connect(connectString: String, sessionTimeout: Int): CuratorFramework {
        stateLock.lock()
        try {
            val curatorFramework = CuratorFrameworkFactory.builder()
                    .connectString(connectString)
                    .sessionTimeoutMs(sessionTimeout)
                    .retryPolicy(ExponentialBackoffRetry(1000, 10))
                    .build()
            curatorFramework.connectionStateListenable.addListener(this)
            curatorFramework.start()
            curatorFramework.blockUntilConnected(connectTimeout.toInt(), TimeUnit.MILLISECONDS)
            connected = true
            return curatorFramework
        } finally {
            stateLock.unlock()
        }
    }

    private fun init() {
        stateLock.lock()
        try {
            zk = connect(connectString, sessionTimeout)
            val electNode = createElectionNode()
            electionNode = electNode
            workerProcess = WorkerProcess(zk, electionNode,
                    this,
                    kafkaProperties,
                    competitionStateService,
                    HostInfo(clusterConfigurationProperties.advertisedHost, clusterConfigurationProperties.advertisedPort),
                    this,
                    validators,
                    matCommandsValidatorRegistry)
            workerProcess.start()
            Runtime.getRuntime().addShutdownHook(thread(start = false) { close() })
            stateQueryService = StateQueryService(this, restTemplate, clusterConfigurationProperties)
            doLeaderElection()
        } finally {
            stateLock.unlock()
        }
    }

    init {
        init()
    }

    private fun doLeaderElection() {
        stateLock.lock()
        try {
            if (connected) {
                val candidates = zk.children.forPath(electionPath)
                val id = electionNode.split("_").last().toLong(10)

                if (candidates?.isNotEmpty() == true) {
                    val minId = candidates.map(ZkUtils::getIdFromPath).min() ?: Long.MAX_VALUE
                    if (id <= minId) {
                        log.info("I am the leader, my Id is $id")
                        leaderProcess = LeaderProcess(this,
                                electionPath,
                                electionNode, listenerStr, this, scheduleService, kafkaProperties, stateQueryService)
                        leaderProcess.start()
                        leader = true
                    } else {
                        log.info("The leader is $minId, my Id is $id")
                        val idToWatch = candidates.map(ZkUtils::getIdFromPath).filter { it < id }.max()
                        val pathToWatch = idToWatch?.let { candidates.first { cand -> ZkUtils.getIdFromPath(cand) == idToWatch } }
                        log.info("Watching for node $pathToWatch")
                        pathToWatch?.let {
                            zk.checkExists().usingWatcher(this).forPath("$electionPath/$it")
                        }
                    }
                } else {
                    log.info("Strange, should be at least one node in the instances list")
                }
            }
        } finally {
            stateLock.unlock()
        }
    }

    private fun createElectionNode(): String = if (connected) {
        ZkUtils.createNode(zk, "$electionPath/compsrv_", CreateMode.EPHEMERAL_SEQUENTIAL)
    } else {
        throw IllegalStateException("Not connected. ")
    }

    fun close() {
        stateLock.lock()
        try {
            zk.close()
            workerProcess.stop()
            if (leader) {
                leaderProcess.stop()
            }
        } catch (e: Exception) {
            log.warn("Exception while closing zookeeper session", e)
        } finally {
            connected = false
            leader = false
            stateLock.unlock()
        }
    }

    fun getCategoriesForCompetition(competitionId: String) = if (leader && ::leaderProcess.isInitialized) {
        leaderProcess.getCategories(competitionId)?.toTypedArray()
    } else {
        throw RuntimeException("The node you are asking is not a leader node, or it is not initialized. Please try again later.")
    }

    fun getCompetitions(status: CompetitionStatus?, creatorId: String?): Array<CompetitionProperties> {
        if (leader && ::leaderProcess.isInitialized) {
            return leaderProcess.getCompetitions(status, creatorId)
        } else {
            throw RuntimeException("The node you are asking is not a leader node, or it is not initialized. Please try again later.")
        }
    }

    fun getDashboardState(competitionId: String) = if (leader && connected) {
        leaderProcess.getDashboardState(competitionId)
    } else {
        throw IllegalStateException("I am not the leader ($leader) or i am not connected ($connected).")
    }

    fun getMatState(matId: String): MatState? {
        return workerProcess.getMatState(matId)
    }
}