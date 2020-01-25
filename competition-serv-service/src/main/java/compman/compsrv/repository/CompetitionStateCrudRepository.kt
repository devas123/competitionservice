package compman.compsrv.repository


import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.model.dto.competition.CompetitionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

interface OnlyStatus {
    fun getStatus(): CompetitionStatus?
}

@Repository
interface CompetitionStateCrudRepository : JpaRepository<CompetitionState, String> {
    @Query("SELECT g.status FROM competition_state g WHERE g.id = ?1", nativeQuery = true)
    fun findStatusById(id: String): OnlyStatus?
}