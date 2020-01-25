package compman.compsrv.repository


import compman.compsrv.jpa.brackets.StageDescriptor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface StageDescriptorCrudRepository : JpaRepository<StageDescriptor, String> {
    fun findByCompetitionId(competitionId: String): List<StageDescriptor>?
    fun deleteAllByCompetitionId(competitionId: String)
}