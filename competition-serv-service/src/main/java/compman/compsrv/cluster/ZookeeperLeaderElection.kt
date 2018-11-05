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
        stateLock.lock()
        try {
            if (isConnected()) {
                if (isLeader()) {
                    listener.onGranted()
                } else {
                    listener.onRevoked()
                }
            }
            return leadershipListeners.add(listener)
        } finally {
            stateLock.unlock()
        }
    }

    override fun removeListener(listener: LeadershipListener): Boolean {
        return leadershipListeners.remove(listener)
    }

    private val leadershipListeners = CopyOnWriteArrayList<LeadershipListener>()

    override fun isConnected() = zk.zookeeperClient.isConnected

    companion object {
        private val log = LoggerFactory.getLogger(ZookeeperLeaderElection::class.java)
    }

    @Volatile
    private var leader = false

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
            return curatorFramework
        } finally {
            stateLock.unlock()
        }
    }

    private fun createElectionNode(): String = if (isConnected()) {
        ZkUtils.createNode(zk, "$electionPath/compsrv_", CreateMode.EPHEMERAL_SEQUENTIAL)
    } else {
        throw IllegalStateException("Not connected. ")
    }


    private fun doLeaderElection() {
        stateLock.lock()
        try {
            if (isConnected()) {
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
        if (isConnected()) {
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
        stateLock.lock()
        try {
            if (newState == ConnectionState.LOST) {
                leadershipListeners.forEach { it.onRevoked() }
                client?.close()
                close()
            } else if (newState == ConnectionState.RECONNECTED) {
                val kk = zk.checkExists().forPath(electionNode)
                if (kk == null) {
                    doLeaderElection()
                }
            }
        } finally {
            stateLock.unlock()
        }
    }

    private fun close() {
        stateLock.lock()
        try {
            zk.close()
        } catch (e: Exception) {
            log.warn("Exception while closing zookeeper session", e)
        } finally {
            leader = false
            stateLock.unlock()
        }
    }
}