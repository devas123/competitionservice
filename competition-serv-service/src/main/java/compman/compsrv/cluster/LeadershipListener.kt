package compman.compsrv.cluster

interface LeadershipListener {
    fun onGranted()
    fun onRevoked()
}
