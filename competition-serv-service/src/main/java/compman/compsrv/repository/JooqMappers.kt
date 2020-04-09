package compman.compsrv.repository

import com.compmanager.compservice.jooq.tables.*
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.brackets.ParentFightReferenceDTO
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
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
    fun periodCollector(rec: GroupedFlux<String, Record>) = PeriodCollector(rec.key()!!)

    fun stageCollector() = StageCollector()

    fun categoryCollector(): Collector<Record, CategoryDescriptorDTO, CategoryDescriptorDTO> = Collector.of(
            Supplier { CategoryDescriptorDTO() }, BiConsumer<CategoryDescriptorDTO, Record> { t, it ->
        val restriction = CategoryRestrictionDTO()
                .setId(it[CategoryRestriction.CATEGORY_RESTRICTION.ID])
                .setType(it[CategoryRestriction.CATEGORY_RESTRICTION.TYPE]?.let { CategoryRestrictionType.values()[it] })
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

    fun fightCollector(): Collector<Record, FightDescriptionDTO, FightDescriptionDTO> = Collector.of(
            Supplier { FightDescriptionDTO().setScores(emptyArray()) },
            BiConsumer { t: FightDescriptionDTO, it: Record ->
                val compscore = if (!it[CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID].isNullOrBlank()) {
                    val cs = CompScoreDTO()
                            .setScore(ScoreDTO().setPenalties(it[CompScore.COMP_SCORE.PENALTIES])
                                    .setAdvantages(it[CompScore.COMP_SCORE.ADVANTAGES])
                                    .setPoints(it[CompScore.COMP_SCORE.POINTS]))
                            .setPlaceholderId(it[CompScore.COMP_SCORE.PLACEHOLDER_ID])
                            .setOrder(it[CompScore.COMP_SCORE.COMP_SCORE_ORDER])
                            .setCompetitorId(it[CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID])
                    arrayOf(cs)
                } else {
                    emptyArray()
                }
                mapFightDescription(t, it, t.scores + compscore)
            }, BinaryOperator { t: FightDescriptionDTO, u: FightDescriptionDTO ->
        t.setScores(t.scores + u.scores)
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

    fun mapFightDescription(t: FightDescriptionDTO, u: Record, compScore: Array<CompScoreDTO>): FightDescriptionDTO =
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
                    .setParentId1(ParentFightReferenceDTO()
                            .setFightId(u[FightDescription.FIGHT_DESCRIPTION.PARENT_1_FIGHT_ID])
                            .setReferenceType(u[FightDescription.FIGHT_DESCRIPTION.PARENT_1_REFERENCE_TYPE]?.let { FightReferenceType.values()[it] }))
                    .setParentId2(ParentFightReferenceDTO()
                            .setFightId(u[FightDescription.FIGHT_DESCRIPTION.PARENT_2_FIGHT_ID])
                            .setReferenceType(u[FightDescription.FIGHT_DESCRIPTION.PARENT_2_REFERENCE_TYPE]?.let { FightReferenceType.values()[it] }))
                    .setNumberInRound(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND])
                    .setStageId(u[FightDescription.FIGHT_DESCRIPTION.STAGE_ID])
                    .setGroupId(u[FightDescription.FIGHT_DESCRIPTION.GROUP_ID])
                    .setRound(u[FightDescription.FIGHT_DESCRIPTION.ROUND])
                    .setRoundType(u[FightDescription.FIGHT_DESCRIPTION.ROUND_TYPE]?.let { StageRoundType.values()[it] })
                    .setNumberOnMat(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT]).setScores(compScore)


}