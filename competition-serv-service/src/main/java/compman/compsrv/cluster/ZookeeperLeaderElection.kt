package compman.compsrv.cluster

import compman.compsrv.config.ClusterConfigurationProperties
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.state.ConnectionState
import org.apache.curator.framework.state.ConnectionStateListener
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class ZookeeperLeaderElection(clusterConfigurationProperties: ClusterConfigurationProperties) : LeaderElection, ConnectionStateListener, Watcher {
    override fun registerListener(listener: LeadershipListener): Boolean {
        return leadershipListeners.add(listener)
    }

    override fun removeListener(listener: LeadershipListener): Boolean {
        return leadershipListeners.remove(listener)
    }

    private val leadershipListeners = CopyOnWriteArrayList<LeadershipListener>()

    override fun isConnected() = connected

    companion object {
        private val log = LoggerFactory.getLogger(ZookeeperLeaderElection::class.java)
    }

    @Volatile
    var connected = false
        private set

    @Volatile
    var leader = false
        private set

    private val zk: CuratorFramework

    private val stateLock = ReentrantLock()

    private val connectString: String = clusterConfigurationProperties.zookeeper.connectionString
    private val sessionTimeout: Int = clusterConfigurationProperties.zookeeper.sessionTimeout
    private val electionPath = "${clusterConfigurationProperties.zookeeper.namespace}${clusterConfigurationProperties.zookeeper.electionPath}"
    private val connectTimeout = clusterConfigurationProperties.zookeeper.connectTimeout
    private val electionNode: String

    init {
        zk = connect(connectString, sessionTimeout)
        electionNode = createElectionNode()
        doLeaderElection()
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

    private fun createElectionNode(): String = if (connected) {
        ZkUtils.createNode(zk, "$electionPath/compsrv_", CreateMode.EPHEMERAL_SEQUENTIAL)
    } else {
        throw IllegalStateException("Not connected. ")
    }


    private fun doLeaderElection() {
        stateLock.lock()
        try {
            if (connected) {
                val candidates = zk.children.forPath(electionPath)
                val id = electionNode.split("_").last().toLong(10)
                if (candidates?.isNotEmpty() == true) {
                    val minId = candidates.asSequence().map(ZkUtils::getIdFromPath).min() ?: Long.MAX_VALUE
                    if (id <= minId) {
                        log.info("I am the leader, my Id is $id")
                        leader = true
                        leadershipListeners.forEach { it.onGranted() }
                    } else {
                        log.info("The leader is $minId, my Id is $id")
                        val idToWatch = candidates.asSequence().map(ZkUtils::getIdFromPath).filter { it < id }.max()
                        val pathToWatch = idToWatch?.let { candidates.first { cand -> ZkUtils.getIdFromPath(cand) == idToWatch } }
                        log.info("Watching for node $pathToWatch")
                        pathToWatch?.let {
                            zk.checkExists().usingWatcher(this).forPath("$electionPath/$it")
                        }
                        leadershipListeners.forEach { it.onRevoked() }
                    }
                } else {
                    log.info("Strange, should be at least one node in the instances list")
                }
            }
        } finally {
            stateLock.unlock()
        }
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

    override fun isLeader() = leader

    override fun stop() = close()

    override fun stateChanged(client: CuratorFramework?, newState: ConnectionState?) {
        if (newState == ConnectionState.LOST) {
            leadershipListeners.forEach { it.onRevoked() }
            client?.close()
            close()
        }
        connected = newState?.isConnected ?: false
    }

    private fun close() {
        stateLock.lock()
        try {
            zk.close()
        } catch (e: Exception) {
            log.warn("Exception while closing zookeeper session", e)
        } finally {
            connected = false
            leader = false
            stateLock.unlock()
        }
    }
}