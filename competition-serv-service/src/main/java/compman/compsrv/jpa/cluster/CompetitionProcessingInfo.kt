package compman.compsrv.jpa.cluster

import io.scalecube.cluster.Member

data class CompetitionProcessingInfo(val member: Member, val competitionIds: Set<String>) {
    fun addCompetitionIds(competitionIds: Set<String>) = copy(competitionIds = this.competitionIds + competitionIds)
}