package compman.compsrv.repository

import compman.compsrv.model.competition.CompetitionState
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Component

@Component
interface CompetitionStateCrudRepository : MongoRepository<CompetitionState, String> {
    fun findByCompetitionId(competitionId: String): CompetitionState
    fun findTop1ByCompetitionId(name: String, sort: Sort): List<CompetitionState>
}