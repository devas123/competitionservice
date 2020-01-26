package compman.compsrv.repository


import compman.compsrv.jpa.competition.CompScore
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Primary
@Transactional(Transactional.TxType.SUPPORTS)
interface CompScoreCrudRepository : JpaRepository<CompScore, String> {
    @Modifying(flushAutomatically = true)
    @Query("UPDATE comp_score c SET advantages = ?3, points = ?2, penalties = ?4 WHERE c.id = ?1", nativeQuery = true)
    fun updateCompScore(id: String, points: Int, advantages: Int, penalties: Int)

    @Modifying(flushAutomatically = true)
    @Query("INSERT INTO comp_score (id, advantages, penalties, points, compscore_competitor_id, compscore_fight_description_id, comp_score_order) values (?1, ?2, ?3, ?4, ?5, ?6, ?7)", nativeQuery = true)
    fun insertCompScore(id: String, advantages: Int, penalties: Int, points: Int,  competitorId: String, fightId: String, index: Int)

    fun deleteAllByCompetitorCompetitionId(competitionId: String)
}