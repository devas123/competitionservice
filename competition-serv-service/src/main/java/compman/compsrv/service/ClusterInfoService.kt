package compman.compsrv.service

import compman.compsrv.cluster.ClusterMember
import compman.compsrv.cluster.ClusterSession

class ClusterInfoService(private val clusterSession: ClusterSession) {

    fun getClusterInfo(): Array<ClusterMember> = clusterSession.getClusterMembers()

}