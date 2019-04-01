package compman.compsrv.repository


import compman.compsrv.jpa.brackets.BracketDescriptor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.MANDATORY)
interface BracketsCrudRepository : JpaRepository<BracketDescriptor, String> {
    fun findByCompetitionId(competitionId: String): List<BracketDescriptor>?
    fun deleteByCompetitionId(competitionId: String)
}