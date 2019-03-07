package compman.compsrv.repository


import compman.compsrv.jpa.competition.CategoryState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface CategoryCrudRepository : JpaRepository<CategoryState, String> {
    fun findByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): CategoryState?

    fun findByCompetitionId(competitionId: String): Array<CategoryState>?
}