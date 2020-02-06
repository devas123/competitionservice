package compman.compsrv.repository

import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.*
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.DashboardPeriodDTO
import compman.compsrv.util.compNotEmpty
import org.jooq.*
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.sql.Timestamp
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Collector

@Repository
class JooqQueries(private val create: DSLContext) {
    fun categoryQuery(competitionId: String): SelectConditionStep<Record> = create.selectFrom(CategoryDescriptor.CATEGORY_DESCRIPTOR
            .join(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION)
            .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION.CATEGORY_DESCRIPTOR_ID))
            .join(CategoryRestriction.CATEGORY_RESTRICTION)
            .on(CategoryDescriptorRestriction.CATEGORY_DESCRIPTOR_RESTRICTION.CATEGORY_RESTRICTION_ID.eq(CategoryRestriction.CATEGORY_RESTRICTION.ID)))
            .where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.eq(competitionId))

    fun getCategory(competitionId: String, categoryId: String): CategoryDescriptorDTO {
        val categoryFlatResults = categoryQuery(competitionId)
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
    fun fightsCountByMatId(competitionId: String, matId: String) = create.fetchCount(FightDescription.FIGHT_DESCRIPTION, FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(matId).and(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId)))

    fun fightsQuery(competitionId: String): SelectConditionStep<Record> =
            create.selectFrom(FightDescription.FIGHT_DESCRIPTION
                    .join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN)
                    .on(MatDescription.MAT_DESCRIPTION.ID.eq(FightDescription.FIGHT_DESCRIPTION.MAT_ID))
                    .join(CompScore.COMP_SCORE)
                    .on(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(FightDescription.FIGHT_DESCRIPTION.ID))
                    .join(Competitor.COMPETITOR)
                    .on(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.eq(Competitor.COMPETITOR.ID)))
                    .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))
                    .and(Competitor.COMPETITOR.COMPETITION_ID.eq(competitionId))


    fun topMatFightsQuery(limit: Int = 100, competitionId: String, matId: String, statuses: Iterable<FightStatus>): SelectLimitPercentStep<Record> = fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.MAT_ID.eq(matId))
            .and(FightDescription.FIGHT_DESCRIPTION.STATUS.`in`(statuses.map { it.ordinal }))
            .and(Competitor.COMPETITOR.FIRST_NAME.isNotNull)
            .orderBy(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND)
            .limit(limit)

    fun fetchFightsByStageId(competitionId: String, stageId: String): Flux<FightDescriptionDTO> = fightsMapping(Flux.from(fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.STAGE_ID.eq(stageId))))

    fun findFightByCompetitionIdAndId(competitionId: String, fightId: String): Mono<FightDescriptionDTO> = fightsMapping(Flux.from(fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.ID.eq(fightId)))).last()

    fun getCategoryIdsForCompetition(competitionId: String): Flux<String> =
            Flux.from(create.select(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID).from(CategoryDescriptor.CATEGORY_DESCRIPTOR).where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.eq(competitionId)))
                    .map { it.into(String::class.java) }

    fun competitorsQuery(competitionId: String): SelectConditionStep<Record> = create.selectFrom(Competitor.COMPETITOR
            .join(CompetitorCategories.COMPETITOR_CATEGORIES, JoinType.JOIN)
            .on(Competitor.COMPETITOR.ID.equal(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID))
            .join(CategoryDescriptor.CATEGORY_DESCRIPTOR)
            .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)))
            .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId))

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

    fun mapToCompetitor(rec: Record, competitionId: String): CompetitorDTO = CompetitorDTO.builder()
            .id(rec[Competitor.COMPETITOR.ID])
            .competitionId(competitionId)
            .academy(AcademyDTO(rec[Competitor.COMPETITOR.ACADEMY_ID], rec[Competitor.COMPETITOR.ACADEMY_NAME]))
            .registrationStatus(RegistrationStatus.values()[rec[Competitor.COMPETITOR.REGISTRATION_STATUS]].name)
            .birthDate(rec[Competitor.COMPETITOR.BIRTH_DATE].toInstant())
            .lastName(rec[Competitor.COMPETITOR.LAST_NAME])
            .firstName(rec[Competitor.COMPETITOR.FIRST_NAME])
            .email(rec[Competitor.COMPETITOR.EMAIL])
            .categories(arrayOf(rec[CategoryDescriptor.CATEGORY_DESCRIPTOR.ID]))
            .build()

    fun fetchCategoryStateByCompetitionIdAndCategoryId(competitionId: String, categoryId: String): Mono<CategoryStateDTO> {
        val cat = getCategory(competitionId, categoryId)
        return getCategoryStateForCategoryDescriptor(competitionId, cat)
    }

    fun fetchDefaulRegistrationGroupByCompetitionIdAndIdNeq(registrationInfoId: String, groupId: String): Mono<com.compmanager.compservice.jooq.tables.pojos.RegistrationGroup> =
            Mono.from(create.selectFrom(RegistrationGroup.REGISTRATION_GROUP).where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.eq(registrationInfoId))
                    .and(RegistrationGroup.REGISTRATION_GROUP.DEFAULT_GROUP.isTrue)
                    .and(RegistrationGroup.REGISTRATION_GROUP.ID.ne(groupId))).map {
                com.compmanager.compservice.jooq.tables.pojos.RegistrationGroup().apply {
                    this.id = it.id
                    this.defaultGroup = it.defaultGroup
                    this.displayName = it.displayName
                    this.registrationFee = it.registrationFee
                    this.registrationInfoId = it.registrationInfoId
                }
            }

    fun fetchRegistrationGroupIdsByPeriodIdAndRegistrationInfoId(registrationInfoId: String, periodId: String): Flux<String> =
            Flux.from(create.select(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_GROUP_ID).from(
                    RegistrationPeriod.REGISTRATION_PERIOD.join(RegGroupRegPeriod.REG_GROUP_REG_PERIOD).on(RegistrationPeriod.REGISTRATION_PERIOD.ID.eq(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID))
            ).where(RegistrationPeriod.REGISTRATION_PERIOD.ID.eq(periodId)).and(RegistrationPeriod.REGISTRATION_PERIOD.REGISTRATION_INFO_ID.eq(registrationInfoId))).map { it.into(String::class.java) }

    fun fetchDistinctMatIdsForSchedule(competitionId: String): Flux<String> = Flux.from(
            create.selectDistinct(FightDescription.FIGHT_DESCRIPTION.MAT_ID).from(FightDescription.FIGHT_DESCRIPTION).where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))
    ).map { it.into(String::class.java) }

    private fun getCategoryStateForCategoryDescriptor(competitionId: String, cat: CategoryDescriptorDTO): Mono<CategoryStateDTO> = Flux.from(competitorsQuery(competitionId)
            .and(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID.eq(cat.id))).map {
        mapCompetitorWithoutCategories(it)
                .setCategories(arrayOf(cat.id))

    }.collectList()
            .flatMap {
                Mono.justOrEmpty(CategoryStateDTO()
                        .setId(cat.id)
                        .setFightsNumber(fightsCountByCategoryId(competitionId, cat.id))
                        .setCompetitors(it?.toTypedArray())
                        .setCategory(cat))
            }


    fun fetchCategoryStatesByCompetitionId(competitionId: String): Flux<CategoryStateDTO> {
        return Flux.from(categoryQuery(competitionId))
                .groupBy { it[CategoryDescriptor.CATEGORY_DESCRIPTOR.ID] }.flatMap { fl ->
                    fl.collect(categoryCollector())
                }
                .flatMap { cat ->
                    getCategoryStateForCategoryDescriptor(competitionId, cat)
                }
    }

    fun getRegistrationGroupPeriodsQuery(competitionId: String): SelectConditionStep<Record> = create.selectFrom(RegistrationGroup.REGISTRATION_GROUP.join(RegGroupRegPeriod.REG_GROUP_REG_PERIOD, JoinType.LEFT_OUTER_JOIN)
            .on(RegistrationGroup.REGISTRATION_GROUP.ID.equal(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_GROUP_ID))
            .join(RegistrationPeriod.REGISTRATION_PERIOD, JoinType.RIGHT_OUTER_JOIN)
            .on(RegistrationPeriod.REGISTRATION_PERIOD.ID.equal(RegGroupRegPeriod.REG_GROUP_REG_PERIOD.REG_PERIOD_ID))
            .join(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES, JoinType.FULL_OUTER_JOIN)
            .on(RegistrationGroup.REGISTRATION_GROUP.ID.equal(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES.REGISTRATION_GROUP_ID)))
            .where(RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_INFO_ID.equal(competitionId))

    fun getCompetitorCategories(competitionId: String, fighterId: String): List<String> =
            create.selectFrom(CategoryDescriptor.CATEGORY_DESCRIPTOR.join(CompetitorCategories.COMPETITOR_CATEGORIES)
                    .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)))
                    .where(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID.equal(fighterId)).and(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.equal(competitionId))
                    .fetch(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID, String::class.java) ?: emptyList()

    fun getCategoryCompetitors(competitionId: String, categoryId: String) =
            create.selectFrom(Competitor.COMPETITOR.join(CompetitorCategories.COMPETITOR_CATEGORIES)
                    .on(Competitor.COMPETITOR.ID.eq(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID)))
                    .where(Competitor.COMPETITOR.COMPETITION_ID.equal(competitionId))
                    .and(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID.equal(categoryId))


    fun fetchDashboardPeriodsByCompeititonId(competitionId: String): Flux<DashboardPeriodDTO> {
        return Flux.from(create
                .selectFrom(DashboardPeriod.DASHBOARD_PERIOD.join(MatDescription.MAT_DESCRIPTION, JoinType.LEFT_OUTER_JOIN).on(DashboardPeriod.DASHBOARD_PERIOD.ID.eq(MatDescription.MAT_DESCRIPTION.DASHBOARD_PERIOD_ID)))
                .where(DashboardPeriod.DASHBOARD_PERIOD.COMPETITION_ID.eq(competitionId)))
                .groupBy { it[DashboardPeriod.DASHBOARD_PERIOD.ID] }
                .flatMap { fl ->
                    fl.collect(Collector.of(Supplier { DashboardPeriodDTO() }, BiConsumer<DashboardPeriodDTO, Record> { t, rec ->
                        val mat = rec[MatDescription.MAT_DESCRIPTION.ID]?.let {
                            MatDescriptionDTO()
                                    .setId(it)
                                    .setDashboardPeriodId(fl.key())
                                    .setName(rec[MatDescription.MAT_DESCRIPTION.NAME])
                        }
                        t
                                .setId(rec[DashboardPeriod.DASHBOARD_PERIOD.ID])
                                .setIsActive(rec[DashboardPeriod.DASHBOARD_PERIOD.IS_ACTIVE])
                                .setName(rec[DashboardPeriod.DASHBOARD_PERIOD.NAME])
                                .setStartTime(rec[DashboardPeriod.DASHBOARD_PERIOD.START_TIME]?.toInstant()).mats = mat?.let { arrayOf(it) }
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

    fun mapFightDescription(t: FightDescriptionDTO, it: Record, compScore: Array<CompScoreDTO>): FightDescriptionDTO =
            t.setId(it[FightDescription.FIGHT_DESCRIPTION.ID])
                    .setCategoryId(it[FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID])
                    .setCompetitionId(it[FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID])
                    .setDuration(it[FightDescription.FIGHT_DESCRIPTION.DURATION])
                    .setFightName(it[FightDescription.FIGHT_DESCRIPTION.FIGHT_NAME])
                    .setFightResult(FightResultDTO()
                            .setResultType(CompetitorResultType.values()[it[FightDescription.FIGHT_DESCRIPTION.RESULT_TYPE]])
                            .setWinnerId(it[FightDescription.FIGHT_DESCRIPTION.WINNER_ID])
                            .setReason(it[FightDescription.FIGHT_DESCRIPTION.REASON]))
                    .setWinFight(it[FightDescription.FIGHT_DESCRIPTION.WIN_FIGHT])
                    .setLoseFight(it[FightDescription.FIGHT_DESCRIPTION.LOSE_FIGHT])
                    .setMat(MatDescriptionDTO()
                            .setId(it[FightDescription.FIGHT_DESCRIPTION.MAT_ID])
                            .setName(it[MatDescription.MAT_DESCRIPTION.NAME]))
                    .setParentId1(ParentFightReferenceDTO()
                            .setFightId(it[FightDescription.FIGHT_DESCRIPTION.PARENT_1_FIGHT_ID])
                            .setReferenceType(FightReferenceType.values()[it[FightDescription.FIGHT_DESCRIPTION.PARENT_1_REFERENCE_TYPE]]))
                    .setParentId2(ParentFightReferenceDTO()
                            .setFightId(it[FightDescription.FIGHT_DESCRIPTION.PARENT_2_FIGHT_ID])
                            .setReferenceType(FightReferenceType.values()[it[FightDescription.FIGHT_DESCRIPTION.PARENT_2_REFERENCE_TYPE]]))
                    .setNumberInRound(it[FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND])
                    .setStageId(it[FightDescription.FIGHT_DESCRIPTION.STAGE_ID])
                    .setNumberOnMat(it[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT]).setScores(compScore)


    private fun fightCollector() = Collector.of(
            Supplier { FightDescriptionDTO() }, BiConsumer<FightDescriptionDTO, Record> { t, it ->
        val compscore = CompScoreDTO()
                .setScore(ScoreDTO().setPenalties(it[CompScore.COMP_SCORE.PENALTIES])
                        .setAdvantages(it[CompScore.COMP_SCORE.ADVANTAGES])
                        .setPoints(it[CompScore.COMP_SCORE.POINTS]))
                .setCompetitor(mapCompetitorWithoutCategories(it))
        mapFightDescription(t, it, arrayOf(compscore))
    }, BinaryOperator { t, u ->
        t.setScores(t.scores + u.scores)
    }, Collector.Characteristics.CONCURRENT, Collector.Characteristics.IDENTITY_FINISH)

    private fun categoryCollector() = Collector.of(
            Supplier { CategoryDescriptorDTO() }, BiConsumer<CategoryDescriptorDTO, Record> { t, it ->
        val restriction = it
                .into(CategoryRestriction.CATEGORY_RESTRICTION.ID,
                        CategoryRestriction.CATEGORY_RESTRICTION.NAME,
                        CategoryRestriction.CATEGORY_RESTRICTION.MIN_VALUE,
                        CategoryRestriction.CATEGORY_RESTRICTION.MAX_VALUE,
                        CategoryRestriction.CATEGORY_RESTRICTION.UNIT).into(CategoryRestrictionDTO::class.java)
        t
                .setId(it[CategoryDescriptor.CATEGORY_DESCRIPTOR.ID])
                .setRegistrationOpen(it[CategoryDescriptor.CATEGORY_DESCRIPTOR.REGISTRATION_OPEN])
                .setFightDuration(it[CategoryDescriptor.CATEGORY_DESCRIPTOR.FIGHT_DURATION])
                .setRestrictions(restriction?.let { arrayOf(it) } ?: emptyArray())
                .name = it[CategoryDescriptor.CATEGORY_DESCRIPTOR.NAME]

    }, BinaryOperator { t, u ->
        t.setRestrictions(t.restrictions + u.restrictions)
    }, Collector.Characteristics.CONCURRENT, Collector.Characteristics.IDENTITY_FINISH)


    fun topMatFights(limit: Int = 100, competitionId: String, matId: String, statuses: Iterable<FightStatus>): Flux<FightDescriptionDTO> {
        val queryResultsFlux =
                Flux.from(topMatFightsQuery(limit, competitionId, matId, statuses))
        return fightsMapping(queryResultsFlux)
    }

    fun fightsMapping(queryResultsFlux: Flux<Record>): Flux<FightDescriptionDTO> = queryResultsFlux.groupBy { rec -> rec[FightDescription.FIGHT_DESCRIPTION.ID] }
            .flatMap { fl ->
                fl.collect(fightCollector())
            }.filter { f -> f.scores?.size == 2 && (f.scores?.all { compNotEmpty(it.competitor) } == true) }

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
                this.id = it.id
                this.matId = it.matId
                this.competitionId = it.competitionId
                this.numberOnMat = it.numberOnMat
                this.categoryId = it.categoryId
                this.startTime = it.startTime
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
                                .set(FightDescription.FIGHT_DESCRIPTION.RESULT_TYPE, fightResult.resultType?.ordinal)
                                .set(FightDescription.FIGHT_DESCRIPTION.STATUS, fightStatus?.ordinal)
                                .where(FightDescription.FIGHT_DESCRIPTION.ID.eq(fightId))

        ).execute()
    }

    private fun fightDescriptionRecord(fight: FightDescriptionDTO) =
            FightDescriptionRecord().apply {
                this.id = fight.id
                this.fightName = fight.fightName
                this.round = fight.round
                this.roundType = fight.roundType?.ordinal
                this.winFight = fight.winFight
                this.loseFight = fight.loseFight
                this.categoryId = fight.categoryId
                this.competitionId = fight.competitionId
                this.parent_1FightId = fight.parentId1?.fightId
                this.parent_1ReferenceType = fight.parentId1?.referenceType?.ordinal
                this.parent_2FightId = fight.parentId2?.fightId
                this.parent_2ReferenceType = fight.parentId2?.referenceType?.ordinal
                this.duration = fight.duration
                this.status = fight.status?.ordinal
                this.winnerId = fight.fightResult?.winnerId
                this.reason = fight.fightResult?.reason
                this.resultType = fight.fightResult?.resultType?.ordinal
                this.matId = fight.mat?.id
                this.numberInRound = fight.numberInRound
                this.numberOnMat = fight.numberOnMat
                this.priority = fight.priority
                this.startTime = fight.startTime?.let { Timestamp.from(it) }
                this.stageId = fight.stageId
                this.period = fight.period
            }

    private fun compscoreRecord(cs: CompScoreDTO, fightId: String) =
            CompScoreRecord().apply {
        this.advantages = cs.score?.advantages
        this.points = cs.score?.points
        this.penalties = cs.score?.penalties
        this.compscoreFightDescriptionId = fightId
        this.compScoreOrder = cs.order
        this.compscoreCompetitorId = cs.competitor?.id!!
    }


    fun dropStages(categoryId: String) {
        create.deleteFrom(StageDescriptor.STAGE_DESCRIPTOR).where(StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID.eq(categoryId)).execute()
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

    fun savePointsAssignments(assignments: List<PointsAssignmentDescriptorDTO>) {
        create.batchInsert(assignments.map {
            PointsAssignmentDescriptorRecord().apply {
                this.id = it.id
                this.classifier = it.classifier?.ordinal
                this.points = it.points
                this.additionalPoints = it.additionalPoints
            }
        }).execute()
    }

    fun saveInputDescriptors(inputDescriptors: List<StageInputDescriptorDTO>) {
        val batch = inputDescriptors.flatMap {
            listOf(create.insertInto(StageInputDescriptor.STAGE_INPUT_DESCRIPTOR,
                    StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.ID, StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.DISTRIBUTION_TYPE,
                    StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.NUMBER_OF_COMPETITORS)
                    .values(it.id, it.distributionType?.ordinal, it.numberOfCompetitors).onDuplicateKeyIgnore()) +
                    it.selectors.flatMap { sel ->
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
                    }
        }
        create.batch(batch).execute()
    }

    private fun saveCompetitorResultQuery(cr: CompetitorResultDTO): InsertReturningStep<CompetitorStageResultRecord> =
            create.insertInto(CompetitorStageResult.COMPETITOR_STAGE_RESULT,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.GROUP_ID, CompetitorStageResult.COMPETITOR_STAGE_RESULT.PLACE,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.POINTS, CompetitorStageResult.COMPETITOR_STAGE_RESULT.ROUND,
                    CompetitorStageResult.COMPETITOR_STAGE_RESULT.COMPETITOR_ID, CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID)
                    .values(cr.groupId, cr.place, cr.points, cr.round, cr.competitorId, cr.stageId).onDuplicateKeyIgnore()

    fun saveCompetitorResults(crs: Iterable<CompetitorResultDTO>) {
        create.batch(crs.map { cr -> saveCompetitorResultQuery(cr) }).execute()
    }


    fun saveResultDescriptors(resultDescriptors: List<StageResultDescriptorDTO>) {
        val batch = resultDescriptors.flatMap {
            it.competitorResults.map { cr ->
                saveCompetitorResultQuery(cr)            }
        }
        create.batch(batch).execute()
    }

    fun saveCategoryDescriptor(c: CategoryDescriptorDTO, competitionId: String) {
        val rec = CategoryDescriptorRecord(c.id, competitionId, c.fightDuration, c.name, c.registrationOpen, null, null)
        val batch = listOf(create.insertInto(CategoryDescriptor.CATEGORY_DESCRIPTOR,
                rec.field1(), rec.field2(), rec.field3(), rec.field4(), rec.field5())
                .values(rec.value1(), rec.value2(), rec.value3(), rec.value4(), rec.value5())) +
                c.restrictions.map {
                    val restRow = CategoryRestrictionRecord(it.id, it.maxValue, it.minValue, it.name, it.type?.ordinal, it.unit)
                    create.insertInto(CategoryRestriction.CATEGORY_RESTRICTION,  restRow.field1(), restRow.field2(),
                            restRow.field3(), restRow.field4(), restRow.field5(), restRow.field6())
                            .values(restRow.value1(), restRow.value2(), restRow.value3(), restRow.value4(),
                                    restRow.value5(), restRow.value6())
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

    fun getCompScoreSize(fightId: String): Int = create.fetchCount(CompScore.COMP_SCORE, CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(fightId))
}