package compman.compsrv.repository

import arrow.core.Tuple2
import arrow.core.Tuple4
import arrow.core.Tuple6
import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.*
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.compNotEmpty
import org.jooq.*
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.sql.Timestamp
import java.time.Instant
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Collector

@Repository
class JooqRepository(private val create: DSLContext, private val queryProvider: JooqQueryProvider, private val jooqMappers: JooqMappers) {
    fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)

    fun getCompetitorNumbersByCategoryIds(competitionId: String): Map<String, Int> = queryProvider.competitorNumbersByCategoryIdsQuery(competitionId)
            .fetch { rec ->
                rec.value1() to (rec.value2() ?: 0)
            }.toMap()


    fun deleteRegGroupRegPeriodById(regGroupId: String, regPeriodId: String) =
            create.deleteFrom(RegGroupRegPeriod.REG_GROUP_REG_PERIOD)
                    .where(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID.eq(regPeriodId))
                    .and(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_GROUP_ID.eq(regGroupId)).execute()


    fun getCategory(competitionId: String, categoryId: String): CategoryDescriptorDTO {
        val categoryFlatResults = queryProvider.categoryQuery(competitionId)
                .and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(categoryId))
                .fetch()
        val categoryRestrictions = categoryFlatResults
                .into(CategoryRestriction.CATEGORY_RESTRICTION.ID,
                        CategoryRestriction.CATEGORY_RESTRICTION.NAME,
                        CategoryRestriction.CATEGORY_RESTRICTION.MIN_VALUE,
                        CategoryRestriction.CATEGORY_RESTRICTION.MAX_VALUE,
                        CategoryRestriction.CATEGORY_RESTRICTION.UNIT).into(CategoryRestrictionDTO::class.java)
        return CategoryDescriptorDTO()
                .setId(categoryFlatResults.getValue(0, CategoryDescriptor.CATEGORY_DESCRIPTOR.ID))
                .setRegistrationOpen(categoryFlatResults.getValue(0, CategoryDescriptor.CATEGORY_DESCRIPTOR.REGISTRATION_OPEN))
                .setFightDuration(categoryFlatResults.getValue(0, CategoryDescriptor.CATEGORY_DESCRIPTOR.FIGHT_DURATION))
                .setRestrictions(categoryRestrictions?.toTypedArray())
                .setName(categoryFlatResults.getValue(0, CategoryDescriptor.CATEGORY_DESCRIPTOR.NAME))
    }

    fun fightsCountByCategoryId(competitionId: String, categoryId: String) = create.fetchCount(FightDescription.FIGHT_DESCRIPTION, FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID.eq(categoryId).and(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId)))
    fun fightsCountByStageId(competitionId: String, stageId: String) = create.fetchCount(FightDescription.FIGHT_DESCRIPTION, FightDescription.FIGHT_DESCRIPTION.STAGE_ID.eq(stageId).and(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId)))
    fun fightsCountByMatId(competitionId: String, matId: String) = create.fetchCount(FightDescription.FIGHT_DESCRIPTION, FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(matId).and(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId)))

    fun fetchFightsByStageId(competitionId: String, stageId: String): Flux<FightDescriptionDTO> = fightsMapping(Flux.from(queryProvider.fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.STAGE_ID.eq(stageId))
            .orderBy(FightDescription.FIGHT_DESCRIPTION.ROUND.asc(),
                    FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND.asc())))

    fun findFightByCompetitionIdAndId(competitionId: String, fightId: String): Mono<FightDescriptionDTO> = fightsMapping(Flux.from(queryProvider.fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.ID.eq(fightId)))).last()

    fun getCategoryIdsForCompetition(competitionId: String): Flux<String> =
            Flux.from(create.select(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID).from(CategoryDescriptor.CATEGORY_DESCRIPTOR).where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.eq(competitionId)))
                    .map { it.into(String::class.java) }

    fun fightsCount(competitionId: String, fighterId: String) =
            create.fetchCount(FightDescription.FIGHT_DESCRIPTION.join(CompScore.COMP_SCORE).on(FightDescription.FIGHT_DESCRIPTION.ID.eq(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID)),
                    CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.eq(fighterId).and(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId)))

    fun competitorsCount(competitionId: String, categoryId: String?) = if (categoryId.isNullOrBlank()) {
        create.fetchCount(Competitor.COMPETITOR, Competitor.COMPETITOR.COMPETITION_ID.eq(competitionId))
    } else {
        create.fetchCount(Competitor.COMPETITOR
                .join(CompetitorCategories.COMPETITOR_CATEGORIES)
                .on(Competitor.COMPETITOR.ID.eq(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID)),
                Competitor.COMPETITOR.COMPETITION_ID.eq(competitionId).and(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID.eq(categoryId)))
    }

    fun mapToCompetitor(rec: Record, competitionId: String): CompetitorDTO = CompetitorDTO()
            .setId(rec[Competitor.COMPETITOR.ID])
            .setCompetitionId(competitionId)
            .setAcademy(AcademyDTO(rec[Competitor.COMPETITOR.ACADEMY_ID], rec[Competitor.COMPETITOR.ACADEMY_NAME]))
            .setRegistrationStatus(RegistrationStatus.values()[rec[Competitor.COMPETITOR.REGISTRATION_STATUS]].name)
            .setBirthDate(rec[Competitor.COMPETITOR.BIRTH_DATE].toInstant())
            .setLastName(rec[Competitor.COMPETITOR.LAST_NAME])
            .setFirstName(rec[Competitor.COMPETITOR.FIRST_NAME])
            .setEmail(rec[Competitor.COMPETITOR.EMAIL])
            .setCategories(arrayOf(rec[CategoryDescriptor.CATEGORY_DESCRIPTOR.ID]))

    fun fetchCategoryStateByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): Mono<CategoryStateDTO> {
        val cat = getCategory(competitionId, categoryId)
        return getCategoryStateForCategoryDescriptor(competitionId, cat)
    }

    fun fetchDefaulRegistrationGroupByCompetitionIdAndIdNeq(registrationInfoId: String, groupId: String): Mono<com.compmanager.compservice.jooq.tables.pojos.RegistrationGroup> =
            Mono.from(create.selectFrom(RegistrationGroup.REGISTRATION_GROUP).where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.eq(registrationInfoId))
                    .and(RegistrationGroup.REGISTRATION_GROUP.DEFAULT_GROUP.isTrue)
                    .and(RegistrationGroup.REGISTRATION_GROUP.ID.ne(groupId))).map {
                com.compmanager.compservice.jooq.tables.pojos.RegistrationGroup().apply {
                    id = it.id
                    defaultGroup = it.defaultGroup
                    displayName = it.displayName
                    registrationFee = it.registrationFee
                    this.registrationInfoId = it.registrationInfoId
                }
            }

    fun fetchRegistrationGroupIdsByPeriodIdAndRegistrationInfoId(registrationInfoId: String, periodId: String): Flux<String> =
            Flux.from(create.select(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_GROUP_ID).from(
                    RegistrationPeriod.REGISTRATION_PERIOD.join(RegGroupRegPeriod.REG_GROUP_REG_PERIOD).on(RegistrationPeriod.REGISTRATION_PERIOD.ID.eq(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID))
            ).where(RegistrationPeriod.REGISTRATION_PERIOD.ID.eq(periodId)).and(RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID.eq(registrationInfoId))).map { it.into(String::class.java) }

    private fun getCategoryStateForCategoryDescriptor(competitionId: String, cat: CategoryDescriptorDTO): Mono<CategoryStateDTO> =
            Mono.justOrEmpty(create
                    .fetchCount(CompetitorCategories.COMPETITOR_CATEGORIES,
                            CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID.eq(cat.id)))
                    .flatMap {
                        Mono.justOrEmpty(CategoryStateDTO()
                                .setCompetitionId(competitionId)
                                .setId(cat.id)
                                .setFightsNumber(fightsCountByCategoryId(competitionId, cat.id))
                                .setNumberOfCompetitors(it)
                                .setCategory(cat))
                    }


    fun fetchCategoryStatesByCompetitionId(competitionId: String): Flux<CategoryStateDTO> {
        return Flux.from(queryProvider.categoryQuery(competitionId))
                .groupBy { it[CategoryDescriptor.CATEGORY_DESCRIPTOR.ID] }.flatMap { fl ->
                    fl.collect(categoryCollector())
                }
                .flatMap { cat ->
                    getCategoryStateForCategoryDescriptor(competitionId, cat)
                }
    }

    fun getCompetitorCategories(competitionId: String, fighterId: String): List<String> =
            create.selectFrom(CategoryDescriptor.CATEGORY_DESCRIPTOR.join(CompetitorCategories.COMPETITOR_CATEGORIES)
                    .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)))
                    .where(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID.equal(fighterId)).and(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.equal(competitionId))
                    .fetch(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID, String::class.java) ?: emptyList()


    fun fetchPeriodsByCompeititonId(competitionId: String): Flux<PeriodDTO> {
        return Flux.from(create
                .selectFrom(SchedulePeriod.SCHEDULE_PERIOD.join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                        .on(SchedulePeriod.SCHEDULE_PERIOD.ID.eq(MatDescription.MAT_DESCRIPTION.PERIOD_ID)))
                .where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(competitionId)))
                .groupBy { it[SchedulePeriod.SCHEDULE_PERIOD.ID] }
                .flatMap { fl ->
                    fl.collect(Collector.of(Supplier { PeriodDTO() }, BiConsumer<PeriodDTO, Record> { t, rec ->
                        val mat = rec[MatDescription.MAT_DESCRIPTION.ID]?.let {
                            MatDescriptionDTO()
                                    .setId(it)
                                    .setPeriodId(fl.key())
                                    .setName(rec[MatDescription.MAT_DESCRIPTION.NAME])
                        }
                        t
                                .setId(rec[SchedulePeriod.SCHEDULE_PERIOD.ID])
                                .setIsActive(rec[SchedulePeriod.SCHEDULE_PERIOD.IS_ACTIVE])
                                .setName(rec[SchedulePeriod.SCHEDULE_PERIOD.NAME])
                                .setStartTime(rec[SchedulePeriod.SCHEDULE_PERIOD.START_TIME]?.toInstant()).mats = mat?.let { arrayOf(it) }
                                ?: emptyArray()
                    }, BinaryOperator { t, u -> t.setMats(t.mats + u.mats) }))
                }
    }

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
                    .setCategoryId(u[FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID])
                    .setCompetitionId(u[FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID])
                    .setDuration(u[FightDescription.FIGHT_DESCRIPTION.DURATION])
                    .setFightName(u[FightDescription.FIGHT_DESCRIPTION.FIGHT_NAME])
                    .setFightResult(FightResultDTO()
                            .setResultTypeId(u[FightDescription.FIGHT_DESCRIPTION.RESULT_TYPE])
//                            .setResultType(u[FightDescription.FIGHT_DESCRIPTION.RESULT_TYPE]?.let { rt -> FightResultOption.values[rt] })
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
                    .setNumberOnMat(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT]).setScores(compScore)


    private fun fightCollector() = Collector.of(
            Supplier { FightDescriptionDTO().setScores(emptyArray()) },
            BiConsumer<FightDescriptionDTO, Record> { t, it ->

                val compscore = if (!it[CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID].isNullOrBlank()) {
                    arrayOf(CompScoreDTO()
                            .setScore(ScoreDTO().setPenalties(it[CompScore.COMP_SCORE.PENALTIES])
                                    .setAdvantages(it[CompScore.COMP_SCORE.ADVANTAGES])
                                    .setPoints(it[CompScore.COMP_SCORE.POINTS]))
                            .setCompetitor(mapCompetitorWithoutCategories(it)))
                } else {
                    emptyArray()
                }
                mapFightDescription(t, it, t.scores + compscore)
            }, BinaryOperator { t, u ->
        t.setScores(t.scores + u.scores)
    }, Collector.Characteristics.CONCURRENT, Collector.Characteristics.IDENTITY_FINISH)

    private fun categoryCollector() = Collector.of(
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


    fun topMatFights(limit: Int = 100, competitionId: String, matId: String, statuses: Iterable<FightStatus>): Flux<FightDescriptionDTO> {
        val queryResultsFlux =
                Flux.from(queryProvider.topMatFightsQuery(limit, competitionId, matId, statuses))
        return fightsMapping(queryResultsFlux)
    }

    fun fightsMapping(queryResultsFlux: Flux<Record>, filterEmptyFights: Boolean = false): Flux<FightDescriptionDTO> = queryResultsFlux.groupBy { rec -> rec[FightDescription.FIGHT_DESCRIPTION.ID] }
            .flatMap { fl ->
                fl.collect(fightCollector())
            }.filter { f ->
                !filterEmptyFights
                        || (f.scores?.size == 2 && (f.scores?.all { compNotEmpty(it.competitor) } == true))
            }

    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStatusNotInOrderByNumberOnMat(matId: String, competitionId: String, minNumberOnMat: Int,
                                                                                                          fightStatuses: List<FightStatus>): Flux<com.compmanager.compservice.jooq.tables.pojos.FightDescription> {
        return Flux.from(fightDescriptionByMatIdCompetitionIdQuery(matId, competitionId)
                .and(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT.greaterOrEqual(minNumberOnMat))
                .and(FightDescription.FIGHT_DESCRIPTION.STATUS.`in`(fightStatuses.map { it.ordinal }))
                .orderBy(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT.asc()))
                .distinct { it.id }
                .map { fightDescription(it) }
    }

    private fun fightDescriptionByMatIdCompetitionIdQuery(matId: String, competitionId: String): SelectConditionStep<FightDescriptionRecord> {
        return create.selectFrom(FightDescription.FIGHT_DESCRIPTION)
                .where(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(matId))
                .and(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))
    }

    private fun fightDescription(it: FightDescriptionRecord) =
            com.compmanager.compservice.jooq.tables.pojos.FightDescription().apply {
                id = it.id
                matId = it.matId
                competitionId = it.competitionId
                numberOnMat = it.numberOnMat
                categoryId = it.categoryId
                startTime = it.startTime
            }

    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatLessThanAndStatusNotInOrderByNumberOnMatDesc(matId: String, competitionId: String, minNumberOnMat: Int,
                                                                                                      fightStatuses: List<FightStatus>): Flux<com.compmanager.compservice.jooq.tables.pojos.FightDescription> {
        return Flux.from(fightDescriptionByMatIdCompetitionIdQuery(matId, competitionId)
                .and(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT.lessThan(minNumberOnMat))
                .and(FightDescription.FIGHT_DESCRIPTION.STATUS.notIn(fightStatuses.map { it.ordinal }))
                .orderBy(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT.desc()))
                .distinct { it.id }
                .map { fightDescription(it) }
    }

    fun findDistinctByMatIdAndCompetitionIdAndNumberOnMatBetweenAndStatusNotInOrderByNumberOnMat(matId: String, competitionId: String, start: Int, end: Int, fightStatuses: List<FightStatus>): Flux<com.compmanager.compservice.jooq.tables.pojos.FightDescription> {
        return Flux.from(fightDescriptionByMatIdCompetitionIdQuery(matId, competitionId)
                .and(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT.between(start, end))
                .and(FightDescription.FIGHT_DESCRIPTION.STATUS.notIn(fightStatuses.map { it.ordinal }))
                .orderBy(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT.asc()))
                .distinct { it.id }
                .map { fightDescription(it) }
    }

    fun saveFights(fights: List<FightDescriptionDTO>) {
        val records = fights.flatMap { fight ->
            listOf(
                    fightDescriptionRecord(fight)
            ) +
                    (fight.scores?.map { cs ->
                        compscoreRecord(cs, fight.id)
                    } ?: emptyList())
        }
        create.batchStore(records).execute()
    }

    fun getDsl() = create

    fun saveEvents(events: List<EventDTO>): IntArray =
            create.batchInsert(events.map { EventRecord(it.id, it.categoryId, it.competitionId, it.correlationId, it.matId, it.payload, it.type?.ordinal) }).execute()

    fun updateFightResult(fightId: String, compScores: List<CompScoreDTO>, fightResult: FightResultDTO, fightStatus: FightStatus) {
        create.batch(
                compScores.map { cs ->
                    create.update(CompScore.COMP_SCORE)
                            .set(CompScore.COMP_SCORE.PENALTIES, cs.score.penalties)
                            .set(CompScore.COMP_SCORE.ADVANTAGES, cs.score.advantages)
                            .set(CompScore.COMP_SCORE.POINTS, cs.score.points)
                            .where(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(fightId))
                            .and(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.eq(cs.competitor.id))
                } +
                        create.update(FightDescription.FIGHT_DESCRIPTION)
                                .set(FightDescription.FIGHT_DESCRIPTION.WINNER_ID, fightResult.winnerId)
                                .set(FightDescription.FIGHT_DESCRIPTION.REASON, fightResult.reason)
                                .set(FightDescription.FIGHT_DESCRIPTION.RESULT_TYPE, fightResult.resultTypeId)
                                .set(FightDescription.FIGHT_DESCRIPTION.STATUS, fightStatus.ordinal)
                                .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(fightId))

        ).execute()
    }

    private fun fightDescriptionRecord(fight: FightDescriptionDTO) =
            FightDescriptionRecord().apply {
                id = fight.id
                fightName = fight.fightName
                round = fight.round
                roundType = fight.roundType?.ordinal
                winFight = fight.winFight
                loseFight = fight.loseFight
                categoryId = fight.categoryId
                competitionId = fight.competitionId
                parent_1FightId = fight.parentId1?.fightId
                parent_1ReferenceType = fight.parentId1?.referenceType?.ordinal
                parent_2FightId = fight.parentId2?.fightId
                parent_2ReferenceType = fight.parentId2?.referenceType?.ordinal
                duration = fight.duration
                status = fight.status?.ordinal
                winnerId = fight.fightResult?.winnerId
                reason = fight.fightResult?.reason
                resultType = fight.fightResult?.resultTypeId
                matId = fight.mat?.id
                numberInRound = fight.numberInRound
                numberOnMat = fight.numberOnMat
                priority = fight.priority
                startTime = fight.startTime?.toTimestamp()
                stageId = fight.stageId
                period = fight.period
                groupId = fight.groupId
            }

    private fun compscoreRecord(cs: CompScoreDTO, fightId: String) =
            CompScoreRecord().apply {
                advantages = cs.score?.advantages
                points = cs.score?.points
                penalties = cs.score?.penalties
                compscoreFightDescriptionId = fightId
                compScoreOrder = cs.order
                compscoreCompetitorId = cs.competitor?.id!!
            }


    fun dropStages(categoryId: String) {
        create.deleteFrom(StageDescriptor.STAGE_DESCRIPTOR).where(StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID.eq(categoryId)).execute()
    }

    fun fetchStagesForCategory(competitionId: String, categoryId: String): Flux<StageDescriptorDTO> = Flux.from(
            create.selectFrom(StageDescriptor.STAGE_DESCRIPTOR
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
                    .where(StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID.eq(competitionId))
                    .and(StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID.eq(categoryId))
    ).groupBy { it[StageDescriptor.STAGE_DESCRIPTOR.ID] }
            .flatMap { records ->
                records.collect({
                    Tuple6(StageDescriptorDTO(),
                            mutableListOf<FightResultOptionDTO>(),
                            Tuple2(StageResultDescriptorDTO(), mutableListOf<CompetitorStageResultDTO>()),
                            Tuple2(StageInputDescriptorDTO(), mutableListOf<Tuple2<CompetitorSelectorDTO, MutableSet<String>>>()),
                            mutableListOf<GroupDescriptorDTO>(),
                            mutableListOf<AdditionalGroupSortingDescriptorDTO>())
                }, { t, u ->
                    addStageFromRecord(t, u)
                })
            }.map { tuple ->
                tuple.a
                        .setGroupDescriptors(tuple.e.toTypedArray())
                        .setStageResultDescriptor(tuple.c.a
                                .setFightResultOptions(tuple.b.toTypedArray())
                                .setCompetitorResults(tuple.c.b.toTypedArray())
                                .setAdditionalGroupSortingDescriptors(tuple.f.toTypedArray()))
                        .setInputDescriptor(tuple.d.a
                                .setSelectors(tuple.d.b.map { db -> db.a.setSelectorValue(db.b.toTypedArray()) }.toTypedArray()))
                        .setNumberOfFights(fightsCountByStageId(tuple.a.competitionId, tuple.a.id))
            }

    private fun addStageFromRecord(t: Tuple6<StageDescriptorDTO, MutableList<FightResultOptionDTO>, Tuple2<StageResultDescriptorDTO, MutableList<CompetitorStageResultDTO>>, Tuple2<StageInputDescriptorDTO, MutableList<Tuple2<CompetitorSelectorDTO, MutableSet<String>>>>, MutableList<GroupDescriptorDTO>, MutableList<AdditionalGroupSortingDescriptorDTO>>, u: Record) {
        t.a.id = u[StageDescriptor.STAGE_DESCRIPTOR.ID]
        t.a.bracketType = BracketType.values()[u[StageDescriptor.STAGE_DESCRIPTOR.BRACKET_TYPE]]
        t.a.categoryId = u[StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID]
        t.a.competitionId = u[StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID]
        t.a.hasThirdPlaceFight = u[StageDescriptor.STAGE_DESCRIPTOR.HAS_THIRD_PLACE_FIGHT]
        t.a.name = u[StageDescriptor.STAGE_DESCRIPTOR.NAME]
        t.a.stageType = StageType.values()[u[StageDescriptor.STAGE_DESCRIPTOR.STAGE_TYPE]]
        t.a.stageOrder = u[StageDescriptor.STAGE_DESCRIPTOR.STAGE_ORDER]
        t.a.stageStatus = u[StageDescriptor.STAGE_DESCRIPTOR.STAGE_STATUS]?.let { StageStatus.values()[it] }
        t.a.waitForPrevious = u[StageDescriptor.STAGE_DESCRIPTOR.WAIT_FOR_PREVIOUS]

        if (!u[FightResultOption.FIGHT_RESULT_OPTION.ID].isNullOrBlank()
                && t.b.none { it.id == u[FightResultOption.FIGHT_RESULT_OPTION.ID] }) {
            t.b.add(FightResultOptionDTO()
                    .setId(u[FightResultOption.FIGHT_RESULT_OPTION.ID])
                    .setWinnerPoints(u[FightResultOption.FIGHT_RESULT_OPTION.WINNER_POINTS])
                    .setWinnerAdditionalPoints(u[FightResultOption.FIGHT_RESULT_OPTION.WINNER_ADDITIONAL_POINTS])
                    .setLoserPoints(u[FightResultOption.FIGHT_RESULT_OPTION.LOSER_POINTS])
                    .setLoserAdditionalPoints(u[FightResultOption.FIGHT_RESULT_OPTION.LOSER_ADDITIONAL_POINTS])
                    .setShortName(u[FightResultOption.FIGHT_RESULT_OPTION.SHORT_NAME])
                    .setDraw(u[FightResultOption.FIGHT_RESULT_OPTION.DRAW])
                    .setDescription(u[FightResultOption.FIGHT_RESULT_OPTION.DESCRIPTION]))
        }
        if (!u[GroupDescriptor.GROUP_DESCRIPTOR.ID].isNullOrBlank()
                && t.e.none { it.id == u[GroupDescriptor.GROUP_DESCRIPTOR.ID] }) {
            t.e.add(GroupDescriptorDTO()
                    .setId(u[GroupDescriptor.GROUP_DESCRIPTOR.ID])
                    .setName(u[GroupDescriptor.GROUP_DESCRIPTOR.NAME])
                    .setSize(u[GroupDescriptor.GROUP_DESCRIPTOR.SIZE]))
        }

        t.c.a.id = u[StageDescriptor.STAGE_DESCRIPTOR.ID]
        t.c.a.name = u[StageDescriptor.STAGE_DESCRIPTOR.NAME] + "-Results"
        if (t.c.b.none {
                    it.competitorId == u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.COMPETITOR_ID]
                            && it.stageId == u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID]
                }
                && !u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID].isNullOrBlank()) {
            t.c.b.add(CompetitorStageResultDTO()
                    .setStageId(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID])
                    .setCompetitorId(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.COMPETITOR_ID])
                    .setPlace(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.PLACE])
                    .setGroupId(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.GROUP_ID])
                    .setRound(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.ROUND])
                    .setPoints(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.POINTS]))
        }

        if (!u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.STAGE_ID].isNullOrBlank()
                && u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_SPECIFIER] != null &&
                t.f.none {
                    it.groupSortSpecifier?.ordinal == u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_SPECIFIER]
                }) {
            t.f.add(AdditionalGroupSortingDescriptorDTO()
                    .setGroupSortDirection(u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_DIRECTION]?.let {
                        GroupSortDirection.values()[it]
                    })
                    .setGroupSortSpecifier(u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_SPECIFIER]?.let {
                        GroupSortSpecifier.values()[it]
                    }))
        }

        t.d.a.id = u[StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.ID]
        t.d.a.numberOfCompetitors = u[StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.NUMBER_OF_COMPETITORS]
        t.d.a.distributionType = u[StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.DISTRIBUTION_TYPE]?.let { DistributionType.values()[it] }
        if (t.d.b.none { it.a.id == u[CompetitorSelector.COMPETITOR_SELECTOR.ID] } && !u[CompetitorSelector.COMPETITOR_SELECTOR.ID].isNullOrBlank()) {
            t.d.b.add(Tuple2(CompetitorSelectorDTO()
                    .setId(u[CompetitorSelector.COMPETITOR_SELECTOR.ID])
                    .setApplyToStageId(u[CompetitorSelector.COMPETITOR_SELECTOR.APPLY_TO_STAGE_ID])
                    .setClassifier(u[CompetitorSelector.COMPETITOR_SELECTOR.CLASSIFIER]?.let { SelectorClassifier.values()[it] })
                    .setLogicalOperator(u[CompetitorSelector.COMPETITOR_SELECTOR.LOGICAL_OPERATOR]?.let { LogicalOperator.values()[it] })
                    .setOperator(u[CompetitorSelector.COMPETITOR_SELECTOR.OPERATOR]?.let { OperatorType.values()[it] }),
                    mutableSetOf(u[CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.SELECTOR_VALUE])))
        } else if (!u[CompetitorSelector.COMPETITOR_SELECTOR.ID].isNullOrBlank()) {
            t.d.b.find { it.a.id == u[CompetitorSelector.COMPETITOR_SELECTOR.ID] }
                    ?.b?.add(u[CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.SELECTOR_VALUE])
        }
    }

    fun changeCompetitorCategories(fighterId: String, oldCategories: List<String>, newCategories: List<String>) {
        if (oldCategories.isNotEmpty()) {
            create.deleteFrom(CompetitorCategories.COMPETITOR_CATEGORIES)
                    .where(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID.`in`(oldCategories)
                            .and(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID.eq(fighterId)))
        }
        val batch = newCategories.map { newCategoryId ->
            create.insertInto(CompetitorCategories.COMPETITOR_CATEGORIES,
                    CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID,
                    CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)
                    .values(fighterId, newCategoryId)
        }
        create.batch(batch).execute()
    }

    fun saveStages(stages: List<StageDescriptorDTO>): IntArray = create.batchInsert(stages.map { stage ->
        StageDescriptorRecord().apply {
            id = stage.id
            bracketType = stage.bracketType?.ordinal
            categoryId = stage.categoryId
            competitionId = stage.competitionId
            hasThirdPlaceFight = stage.hasThirdPlaceFight
            name = stage.name
            stageOrder = stage.stageOrder
            stageType = stage.stageType?.ordinal
            waitForPrevious = stage.waitForPrevious
            stageStatus = stage.stageStatus?.ordinal
        }
    }).execute()

    fun saveGroupDescriptors(stageIdToGroups: List<Pair<String, List<GroupDescriptorDTO>>>): IntArray =
            create.batchInsert(stageIdToGroups.flatMap { groups ->
                groups.second.map { group ->
                    GroupDescriptorRecord().apply {
                        id = group.id
                        name = group.name
                        size = group.size
                        this.stageId = groups.first
                    }
                }
            }).execute()


    fun saveInputDescriptors(inputDescriptors: List<StageInputDescriptorDTO>) {
        val batch = inputDescriptors.flatMap {
            listOf(create.insertInto(StageInputDescriptor.STAGE_INPUT_DESCRIPTOR,
                    StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.ID, StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.DISTRIBUTION_TYPE,
                    StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.NUMBER_OF_COMPETITORS)
                    .values(it.id, it.distributionType?.ordinal, it.numberOfCompetitors).onDuplicateKeyIgnore()) +
                    (it.selectors?.flatMap { sel ->
                        listOf(create.insertInto(CompetitorSelector.COMPETITOR_SELECTOR, CompetitorSelector.COMPETITOR_SELECTOR.ID, CompetitorSelector.COMPETITOR_SELECTOR.APPLY_TO_STAGE_ID,
                                CompetitorSelector.COMPETITOR_SELECTOR.CLASSIFIER, CompetitorSelector.COMPETITOR_SELECTOR.LOGICAL_OPERATOR,
                                CompetitorSelector.COMPETITOR_SELECTOR.OPERATOR, CompetitorSelector.COMPETITOR_SELECTOR.STAGE_INPUT_ID)
                                .values(sel.id, sel.applyToStageId, sel.classifier?.ordinal, sel.logicalOperator?.ordinal, sel.operator?.ordinal, it.id).onDuplicateKeyIgnore()) +
                                (sel.selectorValue?.map { sv ->
                                    create.insertInto(CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE,
                                            CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.SELECTOR_VALUE,
                                            CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.COMPETITOR_SELECTOR_ID)
                                            .values(sv, sel.id)
                                } ?: emptyList())
                    } ?: emptyList())
        }
        create.batch(batch).execute()
    }

    private fun saveCompetitorResultQuery(cr: CompetitorStageResultDTO): InsertReturningStep<CompetitorStageResultRecord> =
            create.insertInto(CompetitorStageResult.COMPETITOR_STAGE_RESULT,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.GROUP_ID, CompetitorStageResult.COMPETITOR_STAGE_RESULT.PLACE,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.POINTS, CompetitorStageResult.COMPETITOR_STAGE_RESULT.ROUND,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.COMPETITOR_ID, CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.CONFLICTING, CompetitorStageResult.COMPETITOR_STAGE_RESULT.ROUND_TYPE)
                    .values(cr.groupId, cr.place, cr.points, cr.round, cr.competitorId, cr.stageId, cr.conflicting, cr.roundType?.ordinal)
                    .onDuplicateKeyIgnore()


    fun saveCompetitorResults(crs: Iterable<CompetitorStageResultDTO>) {
        create.batch(crs.map { cr -> saveCompetitorResultQuery(cr) }).execute()
    }

    fun saveResultDescriptors(resultDescriptors: List<Pair<String, StageResultDescriptorDTO>>) {
        val batch = resultDescriptors.flatMap { pair ->
            (pair.second.competitorResults?.map { cr ->
                saveCompetitorResultQuery(cr)
            } ?: emptyList()) + (pair.second.fightResultOptions?.map {
                queryProvider.saveFightResultOptionQuery(pair.first, it)
            } ?: emptyList()) +
                    (pair.second.additionalGroupSortingDescriptors?.map {
                        queryProvider.saveAdditionalGroupSortingDescriptorQuery(pair.first, it)
                    } ?: emptyList())
        }
        create.batch(batch).execute()
    }

    fun saveCategoryDescriptor(c: CategoryDescriptorDTO, competitionId: String) {
        val rec = CategoryDescriptorRecord().apply {
            this.id = c.id
            this.competitionId = competitionId
            this.fightDuration = c.fightDuration
            this.name = c.name
            this.registrationOpen = c.registrationOpen
        }
        val batch = listOf(create.insertInto(CategoryDescriptor.CATEGORY_DESCRIPTOR,
                rec.field1(), rec.field2(), rec.field3(), rec.field4(), rec.field5())
                .values(rec.value1(), rec.value2(), rec.value3(), rec.value4(), rec.value5())) +
                c.restrictions.map {
                    val restRow = CategoryRestrictionRecord(it.id, it.maxValue, it.minValue, it.name, it.type?.ordinal, it.value, it.alias, it.unit)
                    create.insertInto(CategoryRestriction.CATEGORY_RESTRICTION, restRow.field1(), restRow.field2(),
                            restRow.field3(), restRow.field4(), restRow.field5(), restRow.field6())
                            .values(restRow.value1(), restRow.value2(), restRow.value3(), restRow.value4(),
                                    restRow.value5(), restRow.value6())
                            .onDuplicateKeyIgnore()
                } +
                c.restrictions.map { restr ->
                    create.insertInto(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION,
                            CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION.CATEGORY_RESTRICTION_ID,
                            CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION.CATEGORY_DESCRIPTOR_ID)
                            .values(restr.id, c.id)
                            .onDuplicateKeyIgnore()
                }
        create.batch(batch).execute()
    }

    fun replaceFightScore(fightId: String, value: CompScoreDTO, index: Int) {
        create.batch(
                create.deleteFrom(CompScore.COMP_SCORE).where(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(fightId)).and(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.eq(value.competitor.id)),
                create.insertInto(CompScore.COMP_SCORE, CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID, CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID, CompScore.COMP_SCORE.POINTS, CompScore.COMP_SCORE.PENALTIES,
                        CompScore.COMP_SCORE.ADVANTAGES, CompScore.COMP_SCORE.COMP_SCORE_ORDER).values(value.competitor.id, fightId, value.score.points, value.score.penalties, value.score.advantages, index)
        ).execute()
    }

    fun updateRegistrationInfo(regInfo: RegistrationInfoDTO) {
        create.batch(
                listOf(create.update(RegistrationInfo.REGISTRATION_INFO)
                        .set(RegistrationInfo.REGISTRATION_INFO.REGISTRATION_OPEN, regInfo.registrationOpen)
                        .where(RegistrationInfo.REGISTRATION_INFO.ID.eq(regInfo.id))
                ) +
                        regInfo.registrationPeriods.map { rp ->
                            create.update(RegistrationPeriod.REGISTRATION_PERIOD)
                                    .set(RegistrationPeriod.REGISTRATION_PERIOD.NAME, rp.name)
                                    .set(RegistrationPeriod.REGISTRATION_PERIOD.START_DATE, rp.start?.toTimestamp())
                                    .set(RegistrationPeriod.REGISTRATION_PERIOD.END_DATE, rp.end?.toTimestamp())
                                    .where(RegistrationPeriod.REGISTRATION_PERIOD.ID.eq(rp.id))
                                    .and(RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID.eq(regInfo.id))
                        } +
                        regInfo.registrationGroups.flatMap {
                            queryProvider.updateRegistrationGroupCategoriesQueries(it.id, it.categories.toList()) +
                                    create.update(RegistrationGroup.REGISTRATION_GROUP)
                                            .set(RegistrationGroup.REGISTRATION_GROUP.DISPLAY_NAME, it.displayName)
                                            .set(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_FEE, it.registrationFee)
                                            .where(RegistrationGroup.REGISTRATION_GROUP.ID.eq(it.id))
                                            .and(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.eq(regInfo.id))
                        } +
                        regInfo.registrationPeriods.flatMap {
                            queryProvider.replaceRegPeriodsRegGroupsQueries(it.id, it.registrationGroupIds.toList())
                        }
        ).execute()
    }


    fun updateRegistrationGroupCategories(regGroupId: String, categories: List<String>) {
        create.batch(queryProvider.updateRegistrationGroupCategoriesQueries(regGroupId, categories)).execute()
    }

//    fun replaceRegistrationPeriodRegistrationGroups(periodId: String, groupIds: List<String>) {
//        create.batch(replaceRegPeriodsRegGroupsQueries(periodId, groupIds)).execute()
//    }

    fun addRegistrationGroupsToPeriod(periodId: String, regGroups: List<RegistrationGroupDTO>) {
        val newGroups = regGroups.mapNotNull { it.id }
        create.batch(
                regGroups.map {
                    create.insertInto(RegistrationGroup.REGISTRATION_GROUP, RegistrationGroup.REGISTRATION_GROUP.ID,
                            RegistrationGroup.REGISTRATION_GROUP.DEFAULT_GROUP,
                            RegistrationGroup.REGISTRATION_GROUP.DISPLAY_NAME,
                            RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_FEE,
                            RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID)
                            .values(it.id, it.defaultGroup, it.displayName, it.registrationFee, it.registrationInfoId)
                } +
                        queryProvider.insertGroupIdsForPeriodIdQueries(newGroups, periodId)
        ).execute()
    }

    fun getCompScoreSize(fightId: String): Int = create.fetchCount(CompScore.COMP_SCORE, CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(fightId))

    fun saveRegistrationPeriod(period: RegistrationPeriodDTO?) = period?.let { per ->
        create.batch(
                listOf(create.insertInto(RegistrationPeriod.REGISTRATION_PERIOD,
                        RegistrationPeriod.REGISTRATION_PERIOD.ID,
                        RegistrationPeriod.REGISTRATION_PERIOD.NAME,
                        RegistrationPeriod.REGISTRATION_PERIOD.END_DATE,
                        RegistrationPeriod.REGISTRATION_PERIOD.START_DATE,
                        RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID)
                        .values(per.id, per.name, per.end?.toTimestamp(),
                                per.start?.toTimestamp(), per.competitionId)) + queryProvider.insertGroupIdsForPeriodIdQueries(per.registrationGroupIds?.toList()
                        ?: emptyList(), per.id)
        ).execute()
    }

    fun saveCompetitionState(state: CompetitionStateDTO?) {
        state?.let {
            create.batch(
                    listOf(
                            create.insertInto(CompetitionProperties.COMPETITION_PROPERTIES,
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
                                    .values(state.properties?.id,
                                            state.properties?.competitionName,
                                            state.properties?.bracketsPublished,
                                            state.properties?.schedulePublished,
                                            state.properties?.startDate?.toTimestamp(),
                                            state.properties?.endDate?.toTimestamp(),
                                            state.properties?.emailNotificationsEnabled,
                                            state.properties?.emailTemplate,
                                            state.properties?.creatorId,
                                            state.properties?.timeZone,
                                            state.properties?.status?.ordinal,
                                            state.properties?.creationTimestamp)
                    ) +
                            (state.properties.staffIds?.map {
                                create.insertInto(CompetitionPropertiesStaffIds.COMPETITION_PROPERTIES_STAFF_IDS,
                                        CompetitionPropertiesStaffIds.COMPETITION_PROPERTIES_STAFF_IDS.COMPETITION_PROPERTIES_ID,
                                        CompetitionPropertiesStaffIds.COMPETITION_PROPERTIES_STAFF_IDS.STAFF_ID)
                                        .values(state.properties.id, it)
                            } ?: emptyList()) +
                            (state.properties.promoCodes?.map { promo ->
                                create.insertInto(PromoCode.PROMO_CODE,
                                        PromoCode.PROMO_CODE.ID,
                                        PromoCode.PROMO_CODE.COEFFICIENT,
                                        PromoCode.PROMO_CODE.COMPETITION_ID,
                                        PromoCode.PROMO_CODE.EXPIRE_AT,
                                        PromoCode.PROMO_CODE.START_AT)
                                        .values(promo.id, promo.coefficient, promo.competitionId, promo.expireAt?.toTimestamp(), promo.startAt?.toTimestamp())
                            } ?: emptyList())
            ).execute()
        }
    }

    fun deleteScheudlePeriodsByCompetitionId(competitionId: String) {
        create.deleteFrom(SchedulePeriod.SCHEDULE_PERIOD)
                .where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(competitionId))
    }

    fun saveSchedule(schedule: ScheduleDTO?) = schedule?.let {
        create.batch(it.periods.flatMap { per ->
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
                    } +
                    per.mats.flatMap { ms ->
                        listOf(create.insertInto(MatDescription.MAT_DESCRIPTION,
                                MatDescription.MAT_DESCRIPTION.ID,
                                MatDescription.MAT_DESCRIPTION.PERIOD_ID)
                                .values(ms.id, per.id)) +
                                ms.fightStartTimes.map { f ->
                                    create.update(FightDescription.FIGHT_DESCRIPTION)
                                            .set(FightDescription.FIGHT_DESCRIPTION.PERIOD, per.id)
                                            .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, f.numberOnMat)
                                            .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, f.matId)
                                            .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, f.startTime?.toTimestamp())
                                            .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(f.fightId))
                                }
                    }
        }
        ).execute()
    }

    
    fun batchUpdateFightStartTimesMatPeriodNumber(newFights: List<FightStartTimePairDTO>): IntArray =
            create.batch(newFights.map {
                create.update(FightDescription.FIGHT_DESCRIPTION)
                        .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, it.matId)
                        .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, it.numberOnMat)
                        .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, it.startTime?.toTimestamp())
                        .set(FightDescription.FIGHT_DESCRIPTION.PERIOD, it.periodId)
                        .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(it.fightId))
            }).execute()

    fun batchUpdateStartTimeAndMatAndNumberOnMatById(updates: List<Tuple4<String, Instant, String, Int>>): IntArray =
            create.batch(updates.map {
                create.update(FightDescription.FIGHT_DESCRIPTION)
                        .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, it.c)
                        .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, it.d)
                        .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, it.b.toTimestamp())
                        .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(it.a))
            }).execute()


    fun fetchPeriodsByCompetitionId(competitionId: String): Flux<PeriodDTO> = Flux.from(
            queryProvider.selectPeriodsQuery(competitionId)
    ).groupBy { it[SchedulePeriod.SCHEDULE_PERIOD.ID] }.flatMap { rec ->
        rec.collect(jooqMappers.periodCollector(rec))
    }.map { t -> t.a
            .setMats(t.b.map { tm -> tm.a.setFightStartTimes(tm.b.toTypedArray()) }.toTypedArray())
            .setScheduleEntries(t.c.map {tm ->
                tm.a.setFightIds(tm.b.toTypedArray()).setCategoryIds(tm.c.toTypedArray())}.toTypedArray())}

    fun updateCompetitionStatus(id: String, status: CompetitionStatus) {
        create.update(CompetitionProperties.COMPETITION_PROPERTIES)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.STATUS, status.ordinal)
                .where(CompetitionProperties.COMPETITION_PROPERTIES.ID.eq(id))
                .execute()
    }

    fun updateCompetitionProperties(propertiesDTO: CompetitionPropertiesDTO) {
        create.update(CompetitionProperties.COMPETITION_PROPERTIES)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.BRACKETS_PUBLISHED, propertiesDTO.bracketsPublished)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.SCHEDULE_PUBLISHED, propertiesDTO.schedulePublished)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.EMAIL_NOTIFICATIONS_ENABLED, propertiesDTO.emailNotificationsEnabled)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.EMAIL_TEMPLATE, propertiesDTO.emailTemplate)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.START_DATE, propertiesDTO.startDate?.toTimestamp())
                .set(CompetitionProperties.COMPETITION_PROPERTIES.END_DATE, propertiesDTO.endDate?.toTimestamp())
                .set(CompetitionProperties.COMPETITION_PROPERTIES.TIME_ZONE, propertiesDTO.timeZone)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.COMPETITION_NAME, propertiesDTO.competitionName)
                .set(CompetitionProperties.COMPETITION_PROPERTIES.STATUS, propertiesDTO.status?.ordinal)
                .where(CompetitionProperties.COMPETITION_PROPERTIES.ID.eq(propertiesDTO.id)).execute()
    }
}