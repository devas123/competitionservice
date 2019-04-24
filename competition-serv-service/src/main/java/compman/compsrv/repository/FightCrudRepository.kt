package compman.compsrv.repository


import compman.compsrv.jpa.competition.FightDescription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface FightCrudRepository : JpaRepository<FightDescription, String> {
    fun findByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): List<FightDescription>?
    fun findByCompetitionId(competitionId: String): List<FightDescription>?
    @Query("SELECT count(*) FROM fight_description f WHERE f.category_id = ?1", nativeQuery = true)
    fun countByCategoryId(categoryId: String): Int

    @Modifying
    @Transactional(Transactional.TxType.REQUIRED)
    @Query("UPDATE fight_description f SET f.start_time = ?2 WHERE f.id = ?1", nativeQuery = true)
    fun updateStartTimeById(id: String, startTime: Instant)
}