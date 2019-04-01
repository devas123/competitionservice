package compman.compsrv.repository


import compman.compsrv.jpa.competition.Competitor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface CompetitorCrudRepository : JpaRepository<Competitor, String> {
    fun findByUserIdAndCompetitionId(id: String, competitionId: String): Competitor?


    @Query("select u from Competitor u where u.competitionId = ?1 and (u.firstName like %?2 or u.lastName like %?2)")
    fun findByCompetitionIdAndSearchString(competitionId: String, searchString: String, pageable: Pageable): Page<Competitor>

    @Query("select u from Competitor u where u.competitionId = ?1 and u.categoryId = ?2 and (u.firstName like %?2 or u.lastName like %?2)")
    fun findByCompetitionIdAndCategoryIdAndSearchString(competitionId: String, categoryId: String, searchString: String, pageable: Pageable): Page<Competitor>

    fun findByCompetitionId(competitionId: String, pageable: Pageable): Page<Competitor>
    fun findByCompetitionIdAndCategoryId(competitionId: String, categoryId: String, pageable: Pageable): Page<Competitor>
    fun findByUserIdAndCategoryIdAndCompetitionId(userId: String, categoryId: String, competitionId: String): Optional<Competitor>
    fun findByEmailAndCategoryIdAndCompetitionId(email: String, categoryId: String, competitionId: String): Optional<Competitor>
}