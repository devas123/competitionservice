package compman.compsrv.service

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Named
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CompetitionCleaner(
        private val competitionPropertiesDao: CompetitionPropertiesDao,
        private val create: DSLContext) {

    private fun Named.render(): String {
        return create.render(this)
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun deleteCompetition(competitionId: String) {
        val compIdField: Field<String> = DSL.`val`(competitionId)
        competitionPropertiesDao.deleteById(competitionId)
        create.batch(
                create.delete(RegistrationPeriod.REGISTRATION_PERIOD)
                        .where(RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID.equal(competitionId)),
                create.delete(RegistrationGroup.REGISTRATION_GROUP)
                        .where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.equal(competitionId)),
                create.delete(FightDescription.FIGHT_DESCRIPTION)
                        .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.equal(competitionId)),
                create.delete(StageDescriptor.STAGE_DESCRIPTOR)
                        .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.equal(competitionId)),
                create.query("DELETE from ${CompScore.COMP_SCORE.render()} using ${Competitor.COMPETITOR.render()} where ${CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.render()} = ${Competitor.COMPETITOR.ID.render()} and ${Competitor.COMPETITOR.COMPETITION_ID.render()} = {0}", compIdField),
                create.delete(Competitor.COMPETITOR)
                        .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId)),
                create.delete(CategoryDescriptor.CATEGORY_DESCRIPTOR)
                        .where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.equal(competitionId)),
                create.delete(Event.EVENT)
                        .where(Event.EVENT.COMPETITION_ID.eq(competitionId))
        ).execute()
    }
}
