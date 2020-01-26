package compman.compsrv.service

import compman.compsrv.repository.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import javax.persistence.EntityManager

@Component
class CompetitionCleaner(private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                         private val scheduleCrudRepository: ScheduleCrudRepository,
                         private val competitorCrudRepository: CompetitorCrudRepository,
                         private val stageDescriptorCrudRepository: StageDescriptorCrudRepository,
                         private val compScoreCrudRepository: CompScoreCrudRepository,
                         private val categoryStateCrudRepository: CategoryStateCrudRepository,
                         private val fightCrudRepository: FightCrudRepository,
                         private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                         private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                         private val eventRepository: EventRepository,
                         private val entityManager: EntityManager) {

    @Transactional(propagation = Propagation.REQUIRED)
    fun deleteCompetition(competitionId: String) {
        if (competitionStateCrudRepository.existsById(competitionId)) {
            registrationGroupCrudRepository.deleteAllByRegistrationInfoId(competitionId)
            registrationPeriodCrudRepository.deleteAllByRegistrationInfoId(competitionId)
            compScoreCrudRepository.deleteAllByCompetitorCompetitionId(competitionId)
            scheduleCrudRepository.deleteById(competitionId)
            fightCrudRepository.deleteAllByCompetitionId(competitionId)
            stageDescriptorCrudRepository.deleteAllByCompetitionId(competitionId)
            competitorCrudRepository.deleteAllByCompetitionId(competitionId)
            categoryStateCrudRepository.deleteAllByCompetitionId(competitionId)
            competitionStateCrudRepository.deleteById(competitionId)
        }
        eventRepository.deleteAllByCompetitionId(competitionId)
        entityManager.flush()
    }
}
