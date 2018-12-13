package compman.compsrv.model.competition


data class CompetitionStateSnapshot(
        val competitionId: String,
        val eventPartition: Int,
        val eventOffset: Long,
        val processedEventIds: Set<String>,
        val processedCommandIds: Set<String>,
        val serializedState: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompetitionStateSnapshot

        if (competitionId != other.competitionId) return false
        if (eventPartition != other.eventPartition) return false
        if (eventOffset != other.eventOffset) return false
        if (processedEventIds != other.processedEventIds) return false
        if (processedCommandIds != other.processedCommandIds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = competitionId.hashCode()
        result = 31 * result + eventPartition
        result = 31 * result + eventOffset.hashCode()
        result = 31 * result + processedEventIds.hashCode()
        result = 31 * result + processedCommandIds.hashCode()
        return result
    }
}