package compman.compsrv.repository

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.*
import compman.compsrv.model.dto.brackets.AdditionalGroupSortingDescriptorDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.dto.schedule.ScheduleRequirementDTO
import org.jooq.*
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
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

    fun saveCompetitionPropertiesQuery(properties: CompetitionPropertiesDTO): InsertValuesStep12<CompetitionPropertiesRecord, String, String, Boolean, Boolean, Timestamp, Timestamp, Boolean, String, String, String, Int, Long> {
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
                        properties.status?.ordinal,
                        properties.creationTimestamp)
    }

    fun fightsQuery(competitionId: String): SelectConditionStep<Record> =
            create.selectFrom(FightDescription.FIGHT_DESCRIPTION
                    .join(CompScore.COMP_SCORE, JoinType.LEFT_OUTER_JOIN)
                    .on(FightDescription.FIGHT_DESCRIPTION.ID.eq(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID))
                    .join(Competitor.COMPETITOR, JoinType.LEFT_OUTER_JOIN)
                    .on(Competitor.COMPETITOR.ID.eq(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID))
                    .join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                    .on(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(MatDescription.MAT_DESCRIPTION.ID)))
                    .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))

    fun topMatFightsQuery(limit: Int = 100, competitionId: String, matId: String, statuses: Iterable<FightStatus>): SelectLimitPercentStep<Record> = fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(matId))
            .and(FightDescription.FIGHT_DESCRIPTION.STATUS.`in`(statuses.map { it.ordinal }))
            .and(Competitor.COMPETITOR.FIRST_NAME.isNotNull)
            .orderBy(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND)
            .limit(limit)

    fun competitorsQuery(competitionId: String): SelectConditionStep<Record> = create.selectFrom(Competitor.COMPETITOR
            .join(CompetitorCategories.COMPETITOR_CATEGORIES, JoinType.JOIN)
            .on(Competitor.COMPETITOR.ID.equal(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID))
            .join(CategoryDescriptor.CATEGORY_DESCRIPTOR)
            .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)))
            .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId))

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

    fun saveScheduleQuery(it: ScheduleDTO): List<RowCountQuery> {
        return it.periods.flatMap { per ->
            listOf(create.insertInto(SchedulePeriod.SCHEDULE_PERIOD,
                    SchedulePeriod.SCHEDULE_PERIOD.ID,
                    SchedulePeriod.SCHEDULE_PERIOD.NAME,
                    SchedulePeriod.SCHEDULE_PERIOD.START_TIME,
                    SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID,
                    SchedulePeriod.SCHEDULE_PERIOD.IS_ACTIVE,
                    SchedulePeriod.SCHEDULE_PERIOD.END_TIME,
                    SchedulePeriod.SCHEDULE_PERIOD.RISK_PERCENT,
                    SchedulePeriod.SCHEDULE_PERIOD.TIME_BETWEEN_FIGHTS
            )
                    .values(per.id, per.name, per.startTime?.toTimestamp(), it.id, per.isActive, per.endTime?.toTimestamp(),
                            per.riskPercent, per.timeBetweenFights)) +
                    per.mats.flatMap { ms ->
                        listOf(saveMatQuery(ms)) +
                                ms.fightStartTimes.map { f ->
                                    create.update(FightDescription.FIGHT_DESCRIPTION)
                                            .set(FightDescription.FIGHT_DESCRIPTION.PERIOD, per.id)
                                            .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, f.numberOnMat)
                                            .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, f.matId)
                                            .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, f.startTime?.toTimestamp())
                                            .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(f.fightId))
                                }
                    } +
                    per.scheduleEntries.mapIndexed { _, sch ->
                        create.insertInto(ScheduleEntry.SCHEDULE_ENTRY,
                                ScheduleEntry.SCHEDULE_ENTRY.ID,
                                ScheduleEntry.SCHEDULE_ENTRY.PERIOD_ID,
                                ScheduleEntry.SCHEDULE_ENTRY.DURATION,
                                ScheduleEntry.SCHEDULE_ENTRY.START_TIME,
                                ScheduleEntry.SCHEDULE_ENTRY.SCHEDULE_ORDER,
                                ScheduleEntry.SCHEDULE_ENTRY.END_TIME,
                                ScheduleEntry.SCHEDULE_ENTRY.ENTRY_TYPE,
                                ScheduleEntry.SCHEDULE_ENTRY.DESCRIPTION,
                                ScheduleEntry.SCHEDULE_ENTRY.MAT_ID)
                                .values(sch.id,
                                        per.id,
                                        sch.duration,
                                        sch.startTime?.toTimestamp(),
                                        sch.order,
                                        sch.endTime?.toTimestamp(),
                                        sch.entryType?.ordinal,
                                        sch.description,
                                        sch.matId)
                    } +
                    per.scheduleEntries.flatMap { sch ->
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
                                            .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(fid))
                                }
                    }
        } + (it.periods?.flatMap { per -> per.scheduleRequirements?.toList() ?: emptyList() }
                ?.flatMap { scheduleRequirementDTO -> saveScheduleRequirementsQuery(scheduleRequirementDTO) } ?: emptyList()) +
                (it.periods?.flatMap { per -> per.scheduleEntries?.toList() ?: emptyList() }
                        ?.flatMap { e -> e.requirementIds?.map { req ->
                            create.insertInto(ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT,
                                    ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT.SCHEDULE_ENTRY_ID,
                                    ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT.SCHEDULE_REQUIREMENT_ID)
                                    .values(e.id, req)
                        } ?: emptyList() } ?: emptyList())
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
            this.entryType = schedReqDTO.entryType?.ordinal
            this.force = schedReqDTO.isForce
            this.startTime = schedReqDTO.startTime?.toTimestamp()
            this.endTime = schedReqDTO.endTime?.toTimestamp()
        }
        return listOf(create.insertInto(ScheduleRequirement.SCHEDULE_REQUIREMENT, *record.fields())
                .values(record.value1(), record.value2(), record.value3(), record.value4(),
                        record.value5(), record.value6(), record.value7(), record.value8()),
                create.deleteFrom(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION)
                        .where(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.REQUIREMENT_ID.eq(schedReqDTO.id)),
                create.deleteFrom(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION)
                        .where(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.REQUIREMENT_ID.eq(schedReqDTO.id))) +
                (schedReqDTO.categoryIds?.map {
                    create.insertInto(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION,
                            ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.REQUIREMENT_ID,
                            ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.CATEGORY_ID)
                            .values(schedReqDTO.id, it)
                } ?: emptyList()) +
                (schedReqDTO.fightIds?.map {
                    create.insertInto(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION,
                            ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.REQUIREMENT_ID,
                            ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.FIGHT_ID)
                            .values(schedReqDTO.id, it)
                } ?: emptyList())
    }


    fun saveAdditionalGroupSortingDescriptorQuery(stageId: String, agsd: AdditionalGroupSortingDescriptorDTO): InsertValuesStepN<AdditionalGroupSortingDescriptorRecord> =
            AdditionalGroupSortingDescriptorRecord(stageId, agsd.groupSortDirection?.ordinal, agsd.groupSortSpecifier?.ordinal).let {
                create.insertInto(AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR,
                        *it.fields()).values(it.value1(), it.value2(), it.value3())
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
                .on(FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID.eq(ScheduleEntry.SCHEDULE_ENTRY.ID))
                .join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                .on(MatDescription.MAT_DESCRIPTION.PERIOD_ID.eq(SchedulePeriod.SCHEDULE_PERIOD.ID)))
                .where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(competitionId))
    }


}