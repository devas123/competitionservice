package compman.compsrv.repository

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.*
import compman.compsrv.model.dto.brackets.AdditionalGroupSortingDescriptorDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.dto.schedule.ScheduleRequirementDTO
import compman.compsrv.util.toTimestamp
import org.jooq.*
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.Timestamp

@Component
class JooqQueryProvider(private val create: DSLContext) {
    fun categoryQuery(competitionId: String): SelectConditionStep<Record> = create.selectFrom(CategoryDescriptor.CATEGORY_DESCRIPTOR
            .join(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION)
            .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION.CATEGORY_DESCRIPTOR_ID))
            .join(CategoryRestriction.CATEGORY_RESTRICTION)
            .on(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION.CATEGORY_RESTRICTION_ID.eq(CategoryRestriction.CATEGORY_RESTRICTION.ID)))
            .where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.eq(competitionId))

    fun competitorNumbersByCategoryIdsQuery(competitionId: String): SelectHavingStep<Record2<String, Int>> {
        return create.select(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID, DSL.count())
                .from(Competitor.COMPETITOR.join(CompetitorCategories.COMPETITOR_CATEGORIES).on(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID.eq(Competitor.COMPETITOR.ID)))
                .where(Competitor.COMPETITOR.COMPETITION_ID.eq(competitionId))
                .groupBy(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)
    }

    fun saveCompetitionPropertiesQuery(properties: CompetitionPropertiesDTO): InsertValuesStep12<CompetitionPropertiesRecord, String, String, Boolean, Boolean, Timestamp, Timestamp, Boolean, String, String, String, String, Long>? {
        return create.insertInto(CompetitionProperties.COMPETITION_PROPERTIES,
                CompetitionProperties.COMPETITION_PROPERTIES.ID,
                CompetitionProperties.COMPETITION_PROPERTIES.COMPETITION_NAME,
                CompetitionProperties.COMPETITION_PROPERTIES.BRACKETS_PUBLISHED,
                CompetitionProperties.COMPETITION_PROPERTIES.SCHEDULE_PUBLISHED,
                CompetitionProperties.COMPETITION_PROPERTIES.START_DATE,
                CompetitionProperties.COMPETITION_PROPERTIES.END_DATE,
                CompetitionProperties.COMPETITION_PROPERTIES.EMAIL_NOTIFICATIONS_ENABLED,
                CompetitionProperties.COMPETITION_PROPERTIES.EMAIL_TEMPLATE,
                CompetitionProperties.COMPETITION_PROPERTIES.CREATOR_ID,
                CompetitionProperties.COMPETITION_PROPERTIES.TIME_ZONE,
                CompetitionProperties.COMPETITION_PROPERTIES.STATUS,
                CompetitionProperties.COMPETITION_PROPERTIES.CREATION_TIMESTAMP
        )
                .values(properties.id,
                        properties.competitionName,
                        properties.bracketsPublished,
                        properties.schedulePublished,
                        properties.startDate?.toTimestamp(),
                        properties.endDate?.toTimestamp(),
                        properties.emailNotificationsEnabled,
                        properties.emailTemplate,
                        properties.creatorId,
                        properties.timeZone,
                        properties.status?.name,
                        properties.creationTimestamp)
    }

    fun fightsQuery(competitionId: String): SelectConditionStep<Record> =
            create.selectFrom(FightDescription.FIGHT_DESCRIPTION
                    .join(CompScore.COMP_SCORE, JoinType.LEFT_OUTER_JOIN)
                    .on(FightDescription.FIGHT_DESCRIPTION.ID.eq(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID))
                    .join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                    .on(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(MatDescription.MAT_DESCRIPTION.ID)))
                    .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))

    fun topMatFightsQuery(competitionId: String, matId: String, statuses: Iterable<FightStatus>): SelectSeekStep2<Record, Int, Int> {
        return create.select(*(FightDescription.FIGHT_DESCRIPTION.fields()), *CompScore.COMP_SCORE.fields(),
                *MatDescription.MAT_DESCRIPTION.fields())
                .from(
                        FightDescription.FIGHT_DESCRIPTION.join(CompScore.COMP_SCORE, JoinType.LEFT_OUTER_JOIN)
                                .on(FightDescription.FIGHT_DESCRIPTION.ID.eq(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID))
                                .join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                                .on(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(MatDescription.MAT_DESCRIPTION.ID)))
                .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))
                .and(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(matId))
                .and(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.isNotNull)
                .and(create.selectCount()
                        .from(CompScore.COMP_SCORE)
                        .where(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.isNotNull)
                        .and(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(FightDescription.FIGHT_DESCRIPTION.ID)).asField<Int>().ge(2))
                .and(FightDescription.FIGHT_DESCRIPTION.STATUS.`in`(statuses.map { it.name }))
                .orderBy(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT,
                        FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND)
    }

    fun competitorsQueryBasic(): SelectWhereStep<CompetitorRecord> = create.selectFrom(Competitor.COMPETITOR)

    fun competitorsQueryJoined(): SelectWhereStep<Record> = create.selectFrom(Competitor.COMPETITOR
            .join(CompetitorCategories.COMPETITOR_CATEGORIES, JoinType.JOIN)
            .on(Competitor.COMPETITOR.ID.equal(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID))
            .join(CategoryDescriptor.CATEGORY_DESCRIPTOR)
            .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)))

    fun competitorsQuery(competitionId: String): SelectConditionStep<Record> = competitorsQueryJoined().where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId))

    fun getRegistrationGroupPeriodsQuery(competitionId: String): SelectConditionStep<Record> = create.selectFrom(RegistrationGroup.REGISTRATION_GROUP.join(RegGroupRegPeriod.REG_GROUP_REG_PERIOD, JoinType.LEFT_OUTER_JOIN)
            .on(RegistrationGroup.REGISTRATION_GROUP.ID.equal(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_GROUP_ID))
            .join(RegistrationPeriod.REGISTRATION_PERIOD, JoinType.RIGHT_OUTER_JOIN)
            .on(RegistrationPeriod.REGISTRATION_PERIOD.ID.equal(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID))
            .join(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES, JoinType.FULL_OUTER_JOIN)
            .on(RegistrationGroup.REGISTRATION_GROUP.ID.equal(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES.REGISTRATION_GROUP_ID)))
            .where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.equal(competitionId))

    fun saveFightResultOptionQuery(stageId: String, fro: FightResultOptionDTO): InsertReturningStep<FightResultOptionRecord> =
            create.insertInto(FightResultOption.FIGHT_RESULT_OPTION,
                    FightResultOption.FIGHT_RESULT_OPTION.ID, FightResultOption.FIGHT_RESULT_OPTION.STAGE_ID,
                    FightResultOption.FIGHT_RESULT_OPTION.WINNER_POINTS, FightResultOption.FIGHT_RESULT_OPTION.WINNER_ADDITIONAL_POINTS,
                    FightResultOption.FIGHT_RESULT_OPTION.LOSER_POINTS, FightResultOption.FIGHT_RESULT_OPTION.LOSER_ADDITIONAL_POINTS,
                    FightResultOption.FIGHT_RESULT_OPTION.DRAW, FightResultOption.FIGHT_RESULT_OPTION.DESCRIPTION,
                    FightResultOption.FIGHT_RESULT_OPTION.SHORT_NAME)
                    .values(fro.id, stageId, fro.winnerPoints, fro.winnerAdditionalPoints, fro.loserPoints,
                            fro.loserAdditionalPoints, fro.isDraw, fro.description, fro.shortName).onDuplicateKeyIgnore()

    fun saveScheduleQuery(schedule: ScheduleDTO): List<RowCountQuery> {
        return listOf(resetFightDescriptions(schedule),
                create.deleteFrom(SchedulePeriod.SCHEDULE_PERIOD)
                        .where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(schedule.id))) +
                schedule.periods.map { per -> upsertSchedulePeriod(per, schedule.id) } +
                saveMatsAndUpdateFightStartTimes(schedule) +
                schedule.periods.flatMap { per ->
                    insertPeriodsScheduleEntries(per) +
                            connectEntriesAndCategoriesAndFights(per)
                } +
                saveScheduleRequirements(schedule) +
                connectScheduleEntriesAndRequirements(schedule)
    }

    private fun connectScheduleEntriesAndRequirements(schedule: ScheduleDTO): List<InsertValuesStep2<ScheduleEntryScheduleRequirementRecord, String, String>> {
        return schedule.periods?.flatMap { per -> per.scheduleEntries?.toList().orEmpty() }
                ?.flatMap { e ->
                    e.requirementIds?.map { req ->
                        create.insertInto(ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT,
                                ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT.SCHEDULE_ENTRY_ID,
                                ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT.SCHEDULE_REQUIREMENT_ID)
                                .values(e.id, req)
                    }.orEmpty()
                }.orEmpty()
    }

    private fun saveScheduleRequirements(schedule: ScheduleDTO) =
            schedule.periods?.flatMap { per -> per.scheduleRequirements?.toList().orEmpty() }
                    ?.flatMap { scheduleRequirementDTO -> saveScheduleRequirementsQuery(scheduleRequirementDTO) }.orEmpty()

    private fun saveMatsAndUpdateFightStartTimes(schedule: ScheduleDTO): List<RowCountQuery> {
        return schedule.mats.flatMap { ms ->
            listOf(saveMatQuery(ms)) +
                    ms.fightStartTimes.map { f ->
                        create.update(FightDescription.FIGHT_DESCRIPTION)
                                .set(FightDescription.FIGHT_DESCRIPTION.PERIOD, ms.periodId)
                                .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, f.numberOnMat)
                                .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, f.matId)
                                .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, f.startTime?.toTimestamp())
                                .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(f.fightId))
                    }
        }
    }

    private fun connectEntriesAndCategoriesAndFights(per: PeriodDTO): List<RowCountQuery> {
        return per.scheduleEntries.flatMap { sch ->
            listOf(create.deleteFrom(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY)
                    .where(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID.eq(sch.id))) +
                    sch.categoryIds.map { cat ->
                        create.insertInto(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY,
                                CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.CATEGORY_ID,
                                CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID)
                                .values(cat, sch.id)
                    } +
                    sch.fightIds.map { fid ->
                        create.update(FightDescription.FIGHT_DESCRIPTION)
                                .set(FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID, sch.id)
                                .set(FightDescription.FIGHT_DESCRIPTION.INVALID, false)
                                .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(fid.someId))
                    } +
                    sch.invalidFightIds?.map { fid ->
                        create.update(FightDescription.FIGHT_DESCRIPTION)
                                .set(FightDescription.FIGHT_DESCRIPTION.INVALID, true)
                                .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(fid))
                    }.orEmpty()
        }
    }

    private fun insertPeriodsScheduleEntries(per: PeriodDTO): List<InsertValuesStep10<ScheduleEntryRecord, String, String, BigDecimal, Timestamp, Int, Timestamp, String, String, String, String>> {
        return per.scheduleEntries.map { sch ->
            create.insertInto(ScheduleEntry.SCHEDULE_ENTRY,
                    ScheduleEntry.SCHEDULE_ENTRY.ID,
                    ScheduleEntry.SCHEDULE_ENTRY.PERIOD_ID,
                    ScheduleEntry.SCHEDULE_ENTRY.DURATION,
                    ScheduleEntry.SCHEDULE_ENTRY.START_TIME,
                    ScheduleEntry.SCHEDULE_ENTRY.SCHEDULE_ORDER,
                    ScheduleEntry.SCHEDULE_ENTRY.END_TIME,
                    ScheduleEntry.SCHEDULE_ENTRY.ENTRY_TYPE,
                    ScheduleEntry.SCHEDULE_ENTRY.DESCRIPTION,
                    ScheduleEntry.SCHEDULE_ENTRY.NAME,
                    ScheduleEntry.SCHEDULE_ENTRY.COLOR)
                    .values(sch.id,
                            per.id,
                            sch.duration,
                            sch.startTime?.toTimestamp(),
                            sch.order,
                            sch.endTime?.toTimestamp(),
                            sch.entryType?.name,
                            sch.description,
                            sch.name,
                            sch.color)
        }
    }

    private fun upsertSchedulePeriod(per: PeriodDTO, scheduleId: String): InsertOnDuplicateSetMoreStep<SchedulePeriodRecord> {
        return create.insertInto(SchedulePeriod.SCHEDULE_PERIOD,
                SchedulePeriod.SCHEDULE_PERIOD.ID,
                SchedulePeriod.SCHEDULE_PERIOD.NAME,
                SchedulePeriod.SCHEDULE_PERIOD.START_TIME,
                SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID,
                SchedulePeriod.SCHEDULE_PERIOD.IS_ACTIVE,
                SchedulePeriod.SCHEDULE_PERIOD.END_TIME,
                SchedulePeriod.SCHEDULE_PERIOD.RISK_PERCENT,
                SchedulePeriod.SCHEDULE_PERIOD.TIME_BETWEEN_FIGHTS
        )
                .values(per.id, per.name, per.startTime?.toTimestamp(), scheduleId, per.isActive, per.endTime?.toTimestamp(),
                        per.riskPercent, per.timeBetweenFights).onDuplicateKeyUpdate()
                .set(SchedulePeriod.SCHEDULE_PERIOD.END_TIME, per.endTime?.toTimestamp())
                .set(SchedulePeriod.SCHEDULE_PERIOD.START_TIME, per.startTime?.toTimestamp())
                .set(SchedulePeriod.SCHEDULE_PERIOD.NAME, per.name)
                .set(SchedulePeriod.SCHEDULE_PERIOD.RISK_PERCENT, per.riskPercent)
                .set(SchedulePeriod.SCHEDULE_PERIOD.TIME_BETWEEN_FIGHTS, per.timeBetweenFights)
                .set(SchedulePeriod.SCHEDULE_PERIOD.IS_ACTIVE, per.isActive)
    }

    private fun resetFightDescriptions(schedule: ScheduleDTO): UpdateConditionStep<FightDescriptionRecord> {
        return create.update(FightDescription.FIGHT_DESCRIPTION)
                .set(FightDescription.FIGHT_DESCRIPTION.PERIOD, null as String?)
                .set(FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID, null as String?)
                .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, null as String?)
                .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, null as Int?)
                .set(FightDescription.FIGHT_DESCRIPTION.INVALID, null as Boolean?)
                .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, null as Timestamp?)
                .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(schedule.id))
    }

    fun saveMatQuery(mat: MatDescriptionDTO): InsertReturningStep<MatDescriptionRecord> =
            create.insertInto(MatDescription.MAT_DESCRIPTION, *MatDescription.MAT_DESCRIPTION.fields())
                    .values(mat.id, mat.name, mat.matOrder, mat.periodId)
                    .onDuplicateKeyIgnore()


    fun saveScheduleRequirementsQuery(schedReqDTO: ScheduleRequirementDTO): List<RowCountQuery> {
        val record = ScheduleRequirementRecord().apply {
            this.id = schedReqDTO.id
            this.matId = schedReqDTO.matId
            this.periodId = schedReqDTO.periodId
            this.entryType = schedReqDTO.entryType?.name
            this.force = schedReqDTO.isForce
            this.startTime = schedReqDTO.startTime?.toTimestamp()
            this.endTime = schedReqDTO.endTime?.toTimestamp()
            this.name = schedReqDTO.name
            this.color = schedReqDTO.color
            this.entryOrder = schedReqDTO.entryOrder
            this.durationMinutes = schedReqDTO.durationMinutes
        }
        return listOf(create.insertInto(ScheduleRequirement.SCHEDULE_REQUIREMENT, *record.fields())
                .values(record.value1(), record.value2(), record.value3(), record.value4(),
                        record.value5(), record.value6(), record.value7(), record.value8(), record.value9(),
                        record.value10(), record.value11(), record.value12())
                .onDuplicateKeyUpdate()
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.COLOR, schedReqDTO.color)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.ENTRY_ORDER, schedReqDTO.entryOrder)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.ENTRY_TYPE, schedReqDTO.entryType?.name)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.START_TIME, schedReqDTO.startTime?.toTimestamp())
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.END_TIME, schedReqDTO.endTime?.toTimestamp())
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.DURATION_MINUTES, schedReqDTO.durationMinutes)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.PERIOD_ID, schedReqDTO.periodId)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.MAT_ID, schedReqDTO.matId)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.FORCE, schedReqDTO.isForce)
                .set(ScheduleRequirement.SCHEDULE_REQUIREMENT.NAME, schedReqDTO.name),
                create.deleteFrom(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION)
                        .where(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.REQUIREMENT_ID.eq(schedReqDTO.id)),
                create.deleteFrom(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION)
                        .where(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.REQUIREMENT_ID.eq(schedReqDTO.id))) +
                schedReqDTO.categoryIds?.map {
                    create.insertInto(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION,
                            ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.REQUIREMENT_ID,
                            ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.CATEGORY_ID)
                            .values(schedReqDTO.id, it)
                }.orEmpty() +
                schedReqDTO.fightIds?.map {
                    create.insertInto(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION,
                            ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.REQUIREMENT_ID,
                            ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.FIGHT_ID)
                            .values(schedReqDTO.id, it)
                }.orEmpty()
    }


    fun saveAdditionalGroupSortingDescriptorQuery(stageId: String, agsd: AdditionalGroupSortingDescriptorDTO): InsertReturningStep<AdditionalGroupSortingDescriptorRecord> =
            AdditionalGroupSortingDescriptorRecord(stageId, agsd.groupSortDirection?.name, agsd.groupSortSpecifier?.name).let {
                create.insertInto(AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR,
                        *it.fields()).values(it.value1(), it.value2(), it.value3()).onDuplicateKeyIgnore()
            }

    fun selectStagesByCategoryIdQuery(competitionId: String, categoryId: String): SelectConditionStep<Record> {
        return stageJoinQuery()
                .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.eq(competitionId))
                .and(StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID.eq(categoryId))
    }

    fun selectStagesByCompetitionIdQuery(competitionId: String): SelectSeekStep1<Record, Int> {
        return stageJoinQuery()
                .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.eq(competitionId))
                .orderBy(StageDescriptor.STAGE_DESCRIPTOR.STAGE_ORDER)
    }

    fun selectStagesByIdQuery(competitionId: String, stageId: String): SelectConditionStep<Record> {
        return stageJoinQuery()
                .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.eq(competitionId))
                .and(StageDescriptor.STAGE_DESCRIPTOR.ID.eq(stageId))
    }

    private fun stageJoinQuery(): SelectWhereStep<Record> {
        return create.selectFrom(StageDescriptor.STAGE_DESCRIPTOR
                .join(FightResultOption.FIGHT_RESULT_OPTION, JoinType.LEFT_OUTER_JOIN)
                .on(FightResultOption.FIGHT_RESULT_OPTION.STAGE_ID.eq(StageDescriptor.STAGE_DESCRIPTOR.ID))
                .join(GroupDescriptor.GROUP_DESCRIPTOR, JoinType.LEFT_OUTER_JOIN)
                .on(GroupDescriptor.GROUP_DESCRIPTOR.STAGE_ID.eq(StageDescriptor.STAGE_DESCRIPTOR.ID))
                .join(CompetitorStageResult.COMPETITOR_STAGE_RESULT, JoinType.LEFT_OUTER_JOIN)
                .on(CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID.eq(StageDescriptor.STAGE_DESCRIPTOR.ID))
                .join(StageInputDescriptor.STAGE_INPUT_DESCRIPTOR, JoinType.LEFT_OUTER_JOIN)
                .on(StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.ID.eq(StageDescriptor.STAGE_DESCRIPTOR.ID))
                .join(CompetitorSelector.COMPETITOR_SELECTOR, JoinType.LEFT_OUTER_JOIN)
                .on(CompetitorSelector.COMPETITOR_SELECTOR.STAGE_INPUT_ID.eq(StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.ID))
                .join(AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR, JoinType.LEFT_OUTER_JOIN)
                .on(StageDescriptor.STAGE_DESCRIPTOR.ID.eq(AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.STAGE_ID))
                .join(CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE, JoinType.LEFT_OUTER_JOIN)
                .on(CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.COMPETITOR_SELECTOR_ID.eq(CompetitorSelector.COMPETITOR_SELECTOR.ID)))
    }


    fun replaceRegPeriodsRegGroupsQueries(periodId: String, groupIds: List<String>): List<RowCountQuery> {
        return listOf(create.delete(RegGroupRegPeriod.REG_GROUP_REG_PERIOD).where(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID.eq(periodId))) +
                insertGroupIdsForPeriodIdQueries(groupIds, periodId)
    }

    fun insertGroupIdsForPeriodIdQueries(groupIds: List<String>, periodId: String): List<InsertReturningStep<RegGroupRegPeriodRecord>> {
        return groupIds.map { groupId ->
            create.insertInto(RegGroupRegPeriod.REG_GROUP_REG_PERIOD, RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID, RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_GROUP_ID)
                    .values(periodId, groupId).onDuplicateKeyIgnore()
        }
    }

    fun updateRegistrationGroupCategoriesQueries(regGroupId: String, categories: List<String>): List<RowCountQuery> {
        return listOf(create.deleteFrom(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES)
                .where(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES.REGISTRATION_GROUP_ID.eq(regGroupId))
        ) +
                categories.map { cat ->
                    create.insertInto(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES,
                            RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES.REGISTRATION_GROUP_ID, RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES.CATEGORY_ID)
                            .values(regGroupId, cat)
                }
    }

    fun selectPeriodsQuery(competitionId: String): SelectConditionStep<Record> {
        return create.selectFrom(SchedulePeriod.SCHEDULE_PERIOD
                .join(ScheduleEntry.SCHEDULE_ENTRY, JoinType.LEFT_OUTER_JOIN)
                .on(ScheduleEntry.SCHEDULE_ENTRY.PERIOD_ID.eq(SchedulePeriod.SCHEDULE_PERIOD.ID))
                .join(ScheduleRequirement.SCHEDULE_REQUIREMENT, JoinType.LEFT_OUTER_JOIN)
                .on(ScheduleRequirement.SCHEDULE_REQUIREMENT.PERIOD_ID.eq(SchedulePeriod.SCHEDULE_PERIOD.ID))
                .join(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY, JoinType.LEFT_OUTER_JOIN)
                .on(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID.eq(ScheduleEntry.SCHEDULE_ENTRY.ID))
                .join(FightDescription.FIGHT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                .on(FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID.eq(ScheduleEntry.SCHEDULE_ENTRY.ID)))
                .where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(competitionId))
    }
}