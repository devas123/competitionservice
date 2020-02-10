package compman.compsrv.service

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Named
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CompetitionCleaner(
        private val competitionPropertiesDao: CompetitionPropertiesDao,
        private val create: DSLContext) {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCleaner::class.java)
    }

    private fun Named.render(): String {
        val result = create.render(this)
        log.info(result)
        return result
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun deleteCompetition(competitionId: String) {
        if (competitionPropertiesDao.existsById(competitionId)) {
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
                            .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId))
            ).execute()
        }
    }
}
