package compman.compsrv.repository


import compman.compsrv.jpa.competition.BracketDescriptor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface BracketsDescriptorCrudRepository : JpaRepository<BracketDescriptor, String> {
    fun findByCompetitionId(competitionId: String): List<BracketDescriptor>?

    fun deleteAllByCompetitionId(competitionId: String)
}