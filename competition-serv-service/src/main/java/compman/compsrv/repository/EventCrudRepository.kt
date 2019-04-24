package compman.compsrv.repository


import compman.compsrv.jpa.es.events.EventHolder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EventCrudRepository : JpaRepository<EventHolder, String> {
    fun findByCompetitionId(competitionId: String): Collection<OnlyId>?
}