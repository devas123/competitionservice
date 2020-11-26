package compman.compsrv.service

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class CompetitionCleaner(
        private val competitionPropertiesDao: CompetitionPropertiesDao,
        private val create: DSLContext) {

    fun deleteCompetition(competitionId: String) {
        competitionPropertiesDao.deleteById(competitionId)
        create.batch(
                create.delete(RegistrationPeriod.REGISTRATION_PERIOD)
                        .where(RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID.equal(competitionId)),
                create.delete(RegistrationGroup.REGISTRATION_GROUP)
                        .where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.equal(competitionId)),
                create.delete(RegistrationInfo.REGISTRATION_INFO)
                        .where(RegistrationInfo.REGISTRATION_INFO.ID.equal(competitionId)),
                create.delete(FightDescription.FIGHT_DESCRIPTION)
                        .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.equal(competitionId)),
                create.delete(StageDescriptor.STAGE_DESCRIPTOR)
                        .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.equal(competitionId)),
                create.deleteFrom(CompScore.COMP_SCORE).where(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.`in`(create.select(Competitor.COMPETITOR.ID).from(Competitor.COMPETITOR).where(Competitor.COMPETITOR.COMPETITION_ID.eq(competitionId)))),
                create.delete(Competitor.COMPETITOR)
                        .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId)),
                create.delete(CategoryDescriptor.CATEGORY_DESCRIPTOR)
                        .where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.equal(competitionId)),
                create.delete(Event.EVENT)
                        .where(Event.EVENT.COMPETITION_ID.eq(competitionId))
        ).execute()
    }
}
