package compman.compsrv.cluster

import compman.compsrv.config.ClusterConfigurationProperties
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.recipes.leader.LeaderSelector
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils.CloseableUtils
import java.util.concurrent.CopyOnWriteArrayList


class CuratorLeaderElection(clusterConfigurationProperties: ClusterConfigurationProperties) : LeaderElection, LeaderSelectorListenerAdapter() {

    private val listeners = CopyOnWriteArrayList<LeadershipListener>()

    private val curatorFramework = CuratorFrameworkFactory.builder()
            .connectString(clusterConfigurationProperties.zookeeper.connectionString)
            .sessionTimeoutMs(clusterConfigurationProperties.zookeeper.connectTimeout.toInt())
            .retryPolicy(ExponentialBackoffRetry(1000, 10))
            .build()
    private val leaderSelector: LeaderSelector = LeaderSelector(curatorFramework, clusterConfigurationProperties.zookeeper.electionPath, this)

    init {
        curatorFramework.blockUntilConnected()
        leaderSelector.start()
        leaderSelector.autoRequeue()
    }

    override fun takeLeadership(p0: CuratorFramework?) {
        listeners.forEach { it.onGranted() }
        try {
            while (true) {
                Thread.sleep(500)
            }
        } finally {
            listeners.forEach { it.onRevoked() }
        }
    }

    override fun isLeader(): Boolean = leaderSelector.hasLeadership()

    override fun isConnected(): Boolean = curatorFramework.zookeeperClient.isConnected

    override fun stop() {
        CloseableUtils.closeQuietly(leaderSelector)
        CloseableUtils.closeQuietly(curatorFramework)
    }

    override fun registerListener(listener: LeadershipListener): Boolean {
        if (isLeader()) {
            listener.onGranted()
        }
        return listeners.add(listener)
    }

    override fun removeListener(listener: LeadershipListener): Boolean {
        return listeners.remove(listener)
    }
}