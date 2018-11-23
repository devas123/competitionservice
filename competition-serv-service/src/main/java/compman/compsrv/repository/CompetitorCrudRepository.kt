package compman.compsrv.repository


import compman.compsrv.model.competition.Competitor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.MANDATORY)
interface CompetitorCrudRepository : JpaRepository<Competitor, String> {
    fun findByIdAndCompetitionId(id: String, competitionId: String): Competitor?
}