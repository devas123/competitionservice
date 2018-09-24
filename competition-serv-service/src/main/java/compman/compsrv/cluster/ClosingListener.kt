package compman.compsrv.cluster

import org.apache.zookeeper.KeeperException

interface ClosingListener {
    fun workerClosing(rc: KeeperException.Code)

    fun leaderClosing(rc: KeeperException.Code)
}