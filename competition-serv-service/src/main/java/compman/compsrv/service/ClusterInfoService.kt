package compman.compsrv.service

import compman.compsrv.cluster.ClusterMember
import compman.compsrv.cluster.ClusterOperations

class ClusterInfoService(private val clusterOperations: ClusterOperations) {

    fun getClusterInfo(): Array<ClusterMember> = clusterOperations.getClusterMembers()

}