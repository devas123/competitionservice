package compman.compsrv.repository


import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.competition.FightStage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.stream.Stream
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface FightCrudRepository : JpaRepository<FightDescription, String> {
    fun findDistinctByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): List<FightDescription>?
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


    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStageNotInOrderByNumberOnMat(matId: String, competitionId: String, numberOnMat: Int, stages: List<FightStage>): Stream<FightDescription>?
    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatLessThanAndStageNotInOrderByNumberOnMatDesc(matId: String, competitionId: String, numberOnMat: Int, stages: List<FightStage>): Stream<FightDescription>?
    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatBetweenAndStageNotInOrderByNumberOnMat(matId: String, competitionId: String, numberOnMatStart: Int, numberOnMatEnd: Int, stages: List<FightStage>): Stream<FightDescription>?

    fun findDistinctByCompetitionIdAndMatIdAndStageInAndScoresNotNullOrderByNumberOnMat(competitionId: String, matId: String, stages: List<FightStage>, pageable: Pageable): Page<FightDescription>?
    fun deleteAllByCompetitionId(competitionId: String)
}