package compman.compsrv.repository


import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.competition.FightStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*
import java.util.stream.Stream
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface FightCrudRepository : JpaRepository<FightDescription, String> {
    companion object {
        val finishedStatuses = listOf(FightStatus.UNCOMPLETABLE, FightStatus.FINISHED, FightStatus.WALKOVER)
        val unMovableFightStatuses = finishedStatuses + FightStatus.IN_PROGRESS
        val notFinishedStatuses = listOf(FightStatus.PENDING, FightStatus.IN_PROGRESS, FightStatus.GET_READY, FightStatus.PAUSED)
    }
    fun findDistinctByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): List<FightDescription>?
    @Query("select f from FightDescription f join f.scores sc where f.competitionId = ?1 and sc.competitor.id = ?2")
    fun findByCompetitionIdAndScoresCompetitorId(competitionId: String, competitorId: String): List<FightDescription>?
    fun findByCompetitionId(competitionId: String): List<FightDescription>?
    @Query("SELECT count(*) FROM fight_description f WHERE f.category_id = ?1", nativeQuery = true)
    fun countByCategoryId(categoryId: String): Int

    @Query("SELECT count(*) FROM fight_description f WHERE f.mat_id = ?1", nativeQuery = true)
    fun countByMatId(matId: String): Int

    @Modifying
    @Transactional(Transactional.TxType.REQUIRED)
    @Query("UPDATE fight_description f SET start_time = ?2, mat_id = ?3, number_on_mat = ?4, period = ?5 WHERE f.id = ?1", nativeQuery = true)
    fun updateStartTimeAndMatAndNumberOnMatAndPeriodById(id: String, startTime: Instant, matId: String, numberOnMat: Int, period: String)

    @Modifying
    @Transactional(Transactional.TxType.REQUIRED)
    @Query("UPDATE fight_description f SET start_time = ?2, mat_id = ?3, number_on_mat = ?4 WHERE f.id = ?1", nativeQuery = true)
    fun updateStartTimeAndMatAndNumberOnMatById(id: String, startTime: Instant, matId: String, numberOnMat: Int)


    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStatusNotInOrderByNumberOnMat(matId: String, competitionId: String, numberOnMat: Int, statuses: List<FightStatus>): Stream<FightDescription>?
    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatLessThanAndStatusNotInOrderByNumberOnMatDesc(matId: String, competitionId: String, numberOnMat: Int, statuses: List<FightStatus>): Stream<FightDescription>?
    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatBetweenAndStatusNotInOrderByNumberOnMat(matId: String, competitionId: String, numberOnMatStart: Int, numberOnMatEnd: Int, statuses: List<FightStatus>): Stream<FightDescription>?

    fun findDistinctByCompetitionIdAndMatIdAndStatusInAndScoresNotNullOrderByNumberOnMat(competitionId: String, matId: String, statuses: List<FightStatus>, pageable: Pageable): Page<FightDescription>?
    fun deleteAllByCompetitionId(competitionId: String)

    @Query("select distinct on (f.id) * from fight_description f left join comp_score cs on f.id = cs.compscore_fight_description_id full join competitor c on cs.compscore_competitor_id = c.id  where f.stage_id = ?1", nativeQuery = true)
    fun findAllByStageId(stageId: String): Stream<FightDescription>

    @Query("select f.stage_id, f.win_fight, f.lose_fight from fight_description f where f.id = ?1", nativeQuery = true)
    fun getFightBasicInfo(id : String): Optional<OnlyStageId>
}

interface OnlyStageId {
    fun getStageId(): String?
    fun getWinFight(): String?
    fun getLoseFight(): String?
}
