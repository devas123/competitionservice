package compman.compsrv.repository

import compman.compsrv.model.competition.CompetitionState
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.LockModeType
import javax.persistence.OptimisticLockException

@Transactional(propagation = Propagation.REQUIRED)
@Component
open class CompetitionStateRepository(private val competitionStateCrudRepository: CompetitionStateCrudRepository, private val entityManager: EntityManager) {


    open fun findById(id: String): Optional<CompetitionState> {
        return Optional.ofNullable(entityManager.find(CompetitionState::class.java, id, LockModeType.OPTIMISTIC))
    }

    @Throws(OptimisticLockException::class)
    open fun save(state: CompetitionState): CompetitionState {
        entityManager.lock(state, LockModeType.OPTIMISTIC_FORCE_INCREMENT)
        return competitionStateCrudRepository.save(state)
    }

    open fun delete(id: String) {
        return competitionStateCrudRepository.deleteById(id)
    }

}