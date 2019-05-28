package compman.compsrv.cluster

import java.io.Serializable

data class CompetitionProcessingInfo(val member: MemberWithRestPort, val competitionIds: Set<String>): Serializable {
    fun addCompetitionIds(competitionIds: Set<String>) = copy(competitionIds = this.competitionIds + competitionIds)
}