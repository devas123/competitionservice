package compman.compsrv.service

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.daos.*
import compman.compsrv.repository.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import javax.persistence.EntityManager

@Component
class CompetitionCleaner(private val competitionStateCrudRepository: CompetitionStateDao,
                         private val scheduleCrudRepository: ScheduleDao,
                         private val competitorCrudRepository: ScheduleEntriesDao,
                         private val stageDescriptorCrudRepository: StageDescriptorDao,
                         private val compScoreCrudRepository: CompScoreDao,
                         private val categoryStateCrudRepository: CategoryStateDao,
                         private val fightCrudRepository: CategoryDescriptorDao,
                         private val registrationGroupCrudRepository: RegistrationInfoDao,
                         private val registrationPeriodCrudRepository: RegGroupRegPeriodDao,
                         private val eventRepository: EventDao,
                         private val create: DSLContext) {

    @Transactional(propagation = Propagation.REQUIRED)
    fun deleteCompetition(competitionId: String) {
        if (competitionStateCrudRepository.existsById(competitionId)) {
            val compIdField: Field<String> = DSL.`val`(competitionId)
            scheduleCrudRepository.deleteById(competitionId)
            create.batch(
                    create.delete(RegistrationPeriod.REGISTRATION_PERIOD)
                            .where(RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID.equal(competitionId)),
                            create.delete(RegistrationGroup.REGISTRATION_GROUP)
                    .where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.equal(competitionId)),
                    create.delete(FightDescription.FIGHT_DESCRIPTION)
                            .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.equal(competitionId)),
                    create.delete(StageDescriptor.STAGE_DESCRIPTOR)
                            .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.equal(competitionId)),
                    create.query("DELETE from comp_score cs using competitor com where cs.compscore_competitor_id = com.id and com.competition_id = {0}", compIdField),
                    create.delete(Competitor.COMPETITOR)
                            .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId)),
                    create.delete(CategoryState.CATEGORY_STATE)
                            .where(CategoryState.CATEGORY_STATE.COMPETITION_ID.equal(competitionId))
            ).execute()
            competitionStateCrudRepository.deleteById(competitionId)
        }
    }
}
