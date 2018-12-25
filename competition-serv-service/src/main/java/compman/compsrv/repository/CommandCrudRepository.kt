package compman.compsrv.repository


import compman.compsrv.jpa.es.commands.Command
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

interface OnlyId {
    fun getId(): String
}

@Repository
interface CommandCrudRepository : JpaRepository<Command, String> {
    fun findByCompetitionId(competitionId: String): Optional<List<OnlyId>>
}