package compman.compsrv.repository

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.CompScoreRecord
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.repository.collectors.PeriodCollector
import compman.compsrv.repository.collectors.StageCollector
import org.jooq.Record
import org.springframework.stereotype.Component
import reactor.core.publisher.GroupedFlux
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Collector

@Component
class JooqMappers {

    fun hasFightStartTime(u: Record): Boolean {
        return (!u[FightDescription.FIGHT_DESCRIPTION.ID].isNullOrBlank()
                && !u[FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID].isNullOrBlank() &&
                u[FightDescription.FIGHT_DESCRIPTION.START_TIME] != null
                && u[FightDescription.FIGHT_DESCRIPTION.MAT_ID] == u[MatDescription.MAT_DESCRIPTION.ID])
    }

    fun matDescriptionDTO(u: Record): MatDescriptionDTO {
        return MatDescriptionDTO().setId(u[MatDescription.MAT_DESCRIPTION.ID])
                .setMatOrder(u[MatDescription.MAT_DESCRIPTION.MAT_ORDER])
                .setPeriodId(u[MatDescription.MAT_DESCRIPTION.PERIOD_ID])
                .setName(u[MatDescription.MAT_DESCRIPTION.NAME])
                .setFightStartTimes(emptyArray())
    }

    fun fightStartTimePairDTO(u: Record): FightStartTimePairDTO {
        return FightStartTimePairDTO()
                .setFightId(u[FightDescription.FIGHT_DESCRIPTION.ID])
                .setMatId(u[FightDescription.FIGHT_DESCRIPTION.MAT_ID])
                .setFightCategoryId(u[FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID])
                .setPeriodId(u[FightDescription.FIGHT_DESCRIPTION.PERIOD])
                .setStartTime(u[FightDescription.FIGHT_DESCRIPTION.START_TIME]?.toInstant())
                .setNumberOnMat(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT])
                .setInvalid(u[FightDescription.FIGHT_DESCRIPTION.INVALID])
    }


    fun periodCollector(rec: GroupedFlux<String, Record>) = PeriodCollector(rec.key()!!)

    fun stageCollector() = StageCollector()

    fun categoryCollector(): Collector<Record, CategoryDescriptorDTO, CategoryDescriptorDTO> = Collector.of(
            Supplier { CategoryDescriptorDTO() }, BiConsumer<CategoryDescriptorDTO, Record> { t, it ->
        val restriction = CategoryRestrictionDTO()
                .setId(it[CategoryRestriction.CATEGORY_RESTRICTION.ID])
                .setType(it[CategoryRestriction.CATEGORY_RESTRICTION.TYPE]?.let { CategoryRestrictionType.valueOf(it) })
                .setName(it[CategoryRestriction.CATEGORY_RESTRICTION.NAME])
                .setMinValue(it[CategoryRestriction.CATEGORY_RESTRICTION.MIN_VALUE])
                .setMaxValue(it[CategoryRestriction.CATEGORY_RESTRICTION.MAX_VALUE])
                .setUnit(it[CategoryRestriction.CATEGORY_RESTRICTION.UNIT])

        val oldRestrictions = t.restrictions ?: emptyArray()
        val newRestrictions = restriction?.let { arrayOf(it) } ?: emptyArray()
        t
                .setId(it[CategoryDescriptor.CATEGORY_DESCRIPTOR.ID])
                .setRegistrationOpen(it[CategoryDescriptor.CATEGORY_DESCRIPTOR.REGISTRATION_OPEN])
                .setFightDuration(it[CategoryDescriptor.CATEGORY_DESCRIPTOR.FIGHT_DURATION])
                .setRestrictions(oldRestrictions + newRestrictions)
                .name = it[CategoryDescriptor.CATEGORY_DESCRIPTOR.NAME]

    }, BinaryOperator { t, u ->
        t.setRestrictions(t.restrictions + u.restrictions)
    }, Collector.Characteristics.CONCURRENT, Collector.Characteristics.IDENTITY_FINISH)


    fun mapCompetitorWithoutCategories(it: Record): CompetitorDTO = CompetitorDTO()
            .setFirstName(it[Competitor.COMPETITOR.FIRST_NAME])
            .setLastName(it[Competitor.COMPETITOR.LAST_NAME])
            .setAcademy(AcademyDTO()
                    .setName(it[Competitor.COMPETITOR.ACADEMY_NAME])
                    .setId(it[Competitor.COMPETITOR.ACADEMY_ID]))
            .setBirthDate(it[Competitor.COMPETITOR.BIRTH_DATE]?.toInstant())
            .setEmail(it[Competitor.COMPETITOR.EMAIL])
            .setId(it[Competitor.COMPETITOR.ID])
            .setUserId(it[Competitor.COMPETITOR.USER_ID])
            .setCompetitionId(it[Competitor.COMPETITOR.COMPETITION_ID])
            .setPromo(it[Competitor.COMPETITOR.PROMO])

    fun mapFightDescription(t: FightDescriptionDTO, u: Record): FightDescriptionDTO =
            t.setId(u[FightDescription.FIGHT_DESCRIPTION.ID])
                    .setInvalid(u[FightDescription.FIGHT_DESCRIPTION.INVALID])
                    .setCategoryId(u[FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID])
                    .setCompetitionId(u[FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID])
                    .setDuration(u[FightDescription.FIGHT_DESCRIPTION.DURATION])
                    .setFightName(u[FightDescription.FIGHT_DESCRIPTION.FIGHT_NAME])
                    .setFightResult(FightResultDTO()
                            .setResultTypeId(u[FightDescription.FIGHT_DESCRIPTION.RESULT_TYPE])
                            .setWinnerId(u[FightDescription.FIGHT_DESCRIPTION.WINNER_ID])
                            .setReason(u[FightDescription.FIGHT_DESCRIPTION.REASON]))
                    .setWinFight(u[FightDescription.FIGHT_DESCRIPTION.WIN_FIGHT])
                    .setLoseFight(u[FightDescription.FIGHT_DESCRIPTION.LOSE_FIGHT])
                    .setMat(MatDescriptionDTO()
                            .setId(u[FightDescription.FIGHT_DESCRIPTION.MAT_ID])
                            .setName(u[MatDescription.MAT_DESCRIPTION.NAME]))
                    .setNumberInRound(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND])
                    .setStageId(u[FightDescription.FIGHT_DESCRIPTION.STAGE_ID])
                    .setGroupId(u[FightDescription.FIGHT_DESCRIPTION.GROUP_ID])
                    .setRound(u[FightDescription.FIGHT_DESCRIPTION.ROUND])
                    .setStatus(u[FightDescription.FIGHT_DESCRIPTION.STATUS]?.let { FightStatus.valueOf(it) })
                    .setRoundType(u[FightDescription.FIGHT_DESCRIPTION.ROUND_TYPE]?.let { StageRoundType.valueOf(it) })
                    .setNumberOnMat(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT])

    fun compScore(u: CompScoreRecord): CompScoreDTO =
            CompScoreDTO()
                    .setScore(ScoreDTO()
                            .setPenalties(u[CompScore.COMP_SCORE.PENALTIES])
                            .setPoints(u[CompScore.COMP_SCORE.POINTS])
                            .setAdvantages(u[CompScore.COMP_SCORE.ADVANTAGES]))
                    .setCompetitorId(u[CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID])
                    .setOrder(u[CompScore.COMP_SCORE.COMP_SCORE_ORDER])
                    .setParentFightId(u[CompScore.COMP_SCORE.PARENT_FIGHT_ID])
                    .setParentReferenceType(u[CompScore.COMP_SCORE.PARENT_REFERENCE_TYPE]?.let { FightReferenceType.valueOf(it) })
                    .setPlaceholderId(u[CompScore.COMP_SCORE.PLACEHOLDER_ID])


}