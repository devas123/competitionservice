package compman.compsrv.repository


import compman.compsrv.model.competition.CompetitionProperties
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CompetitionPropertiesCrudRepository : MongoRepository<CompetitionProperties, String> {
    fun findByCompetitionId(competitionId: String): CompetitionProperties?
}