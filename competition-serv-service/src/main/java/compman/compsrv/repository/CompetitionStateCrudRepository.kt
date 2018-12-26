package compman.compsrv.repository


import compman.compsrv.jpa.competition.CompetitionState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CompetitionStateCrudRepository : JpaRepository<CompetitionState, String>