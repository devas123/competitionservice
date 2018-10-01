package compman.compsrv.cluster

interface LeaderElection {

    fun isLeader(): Boolean

    fun isConnected(): Boolean

    fun stop()

    fun registerListener(listener: LeadershipListener): Boolean

    fun removeListener(listener: LeadershipListener): Boolean

}