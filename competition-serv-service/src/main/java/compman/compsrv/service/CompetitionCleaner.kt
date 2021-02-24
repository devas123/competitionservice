package compman.compsrv.service

import compman.compsrv.repository.RocksDBRepository
import org.springframework.stereotype.Component

@Component
class CompetitionCleaner(private val rocksDBRepository: RocksDBRepository) {
    fun deleteCompetition(competitionId: String) {
        rocksDBRepository.getOperations().deleteCompetition(competitionId)
    }
}
