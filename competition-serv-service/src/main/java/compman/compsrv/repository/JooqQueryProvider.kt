package compman.compsrv.repository

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.AdditionalGroupSortingDescriptorRecord
import com.compmanager.compservice.jooq.tables.records.FightResultOptionRecord
import com.compmanager.compservice.jooq.tables.records.RegGroupRegPeriodRecord
import compman.compsrv.model.dto.brackets.AdditionalGroupSortingDescriptorDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.FightStatus
import org.jooq.*
import org.jooq.impl.DSL
import org.springframework.stereotype.Component

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
                .join(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY, JoinType.LEFT_OUTER_JOIN)
                .on(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID.eq(ScheduleEntry.SCHEDULE_ENTRY.ID))
                .join(FightDescription.FIGHT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                .on(FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID.eq(ScheduleEntry.SCHEDULE_ENTRY.ID))
                .join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                .on(MatDescription.MAT_DESCRIPTION.PERIOD_ID.eq(SchedulePeriod.SCHEDULE_PERIOD.ID)))
                .where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(competitionId))
    }


}