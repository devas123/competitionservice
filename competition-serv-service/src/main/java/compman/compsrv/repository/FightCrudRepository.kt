package compman.compsrv.repository


import compman.compsrv.jpa.competition.FightDescription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.MANDATORY)
interface FightCrudRepository : JpaRepository<FightDescription, String> {
    fun findByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): List<FightDescription>?

    @Query("SELECT count(*) FROM fight_description f WHERE f.category_id = ?1", nativeQuery = true)
    fun coutByCategoryId(categoryId: String): Int
}