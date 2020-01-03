package compman.compsrv.repository


import compman.compsrv.jpa.competition.CategoryState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface CategoryStateCrudRepository : JpaRepository<CategoryState, String> {
    @Query("SELECT * FROM category_state c WHERE c.id = ?1 and c.competition_id = ?2", nativeQuery = true)
    fun findByIdAndCompetitionId(id: String, competitionId: String): CategoryState?

    @Query("SELECT * FROM category_state c WHERE c.competition_id = ?1", nativeQuery = true)
    fun findByCompetitionId(competitionId: String): Set<CategoryState>?
}