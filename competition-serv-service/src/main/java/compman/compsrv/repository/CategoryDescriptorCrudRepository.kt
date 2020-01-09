package compman.compsrv.repository


import compman.compsrv.jpa.competition.CategoryDescriptor
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
@Primary
interface CategoryDescriptorCrudRepository : JpaRepository<CategoryDescriptor, String> {
    fun deleteAllByCompetitionId(competitionId: String)
}