package compman.compsrv.repository


import compman.compsrv.jpa.competition.Competitor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
@Transactional(Transactional.TxType.SUPPORTS)
interface CompetitorCrudRepository : JpaRepository<Competitor, String> {
    @Query("select u from Competitor u where u.competitionId = ?1 and (u.firstName like %?2 or u.lastName like %?2)")
    fun findByCompetitionIdAndSearchString(competitionId: String, searchString: String, pageable: Pageable): Page<Competitor>

    @Query("select u from Competitor u join u.categories c where u.competitionId = ?1 and c.id = ?2 and (u.firstName like %?3 or u.lastName like %?3)")
    fun findByCompetitionIdAndCategoryIdAndSearchString(competitionId: String, categoryId: String, searchString: String, pageable: Pageable): Page<Competitor>

    fun findByCompetitionId(competitionId: String, pageable: Pageable): Page<Competitor>
    @Query("select u from Competitor u join u.categories c where u.competitionId = ?1 and c.id in ?2")
    fun findByCompetitionIdAndCategoriesContaining(competitionId: String, categories: Iterable<String>, pageable: Pageable): Page<Competitor>

    @Query("select count(u) from Competitor u join u.categories c where u.competitionId = ?1 and c.id in ?2")
    fun countByCompetitionIdAndCategoriesContaining(competitionId: String, categories: Set<String>): Long

    fun deleteAllByCompetitionId(competitionId: String)
}