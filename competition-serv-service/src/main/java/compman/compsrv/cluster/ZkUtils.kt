package compman.compsrv.cluster

import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.ZooDefs


object ZkUtils {


    fun getIdFromPath(path: String): Long = path.split("_")[1].toLong(10)

    fun createNode(zooKeeper: CuratorFramework, path: String, createMode: CreateMode = CreateMode.PERSISTENT, body: ByteArray = ByteArray(0)): String {
        zooKeeper.createContainers(path.dropLastWhile { c -> c != '/' }.dropLast(1))

        return zooKeeper.create().withMode(createMode).withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE).forPath(path, body)
    }
}