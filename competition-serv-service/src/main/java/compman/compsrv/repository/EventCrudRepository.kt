package compman.compsrv.repository


import compman.compsrv.model.es.events.EventHolder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
interface EventCrudRepository : JpaRepository<EventHolder, String> {
    fun findByCompetitionId(competitionId: String): List<EventHolder>
}