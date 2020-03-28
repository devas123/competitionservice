package compman.compsrv.repository

import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.records.*
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import org.jooq.*
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.GroupedFlux
import reactor.core.publisher.Mono
import java.sql.Timestamp
import java.time.Instant

fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)

@Repository
class JooqRepository(private val create: DSLContext, private val queryProvider: JooqQueryProvider, private val jooqMappers: JooqMappers) {

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

    fun fetchFightsByStageId(competitionId: String, stageId: String): Flux<FightDescriptionDTO> =
            fightsMapping(Flux.from(queryProvider.fightsQuery(competitionId)
                    .and(FightDescription.FIGHT_DESCRIPTION.STAGE_ID.eq(stageId))
                    .orderBy(FightDescription.FIGHT_DESCRIPTION.ROUND.asc(),
                            FightDescription.FIGHT_DESCRIPTION.NUMBER_IN_ROUND.asc())))

    fun findFightByCompetitionIdAndId(competitionId: String, fightId: String): Mono<FightDescriptionDTO> = fightsMapping(Flux.from(queryProvider.fightsQuery(competitionId)
            .and(FightDescription.FIGHT_DESCRIPTION.ID.eq(fightId)))).last()

    fun getCategoryIdsForCompetition(competitionId: String): Flux<String> =
            Flux.from(create.select(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID).from(CategoryDescriptor.CATEGORY_DESCRIPTOR).where(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.eq(competitionId)))
                    .map { it.into(String::class.java) }

    fun getFightIdsForCategory(vararg categoryIds: String): Flux<String> =
            Flux.from(create.select(FightDescription.FIGHT_DESCRIPTION.ID).from(FightDescription.FIGHT_DESCRIPTION).where(FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID.`in`(*categoryIds)))
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
                    fl.collect(jooqMappers.categoryCollector())
                }
                .flatMap { cat ->
                    getCategoryStateForCategoryDescriptor(competitionId, cat)
                }
    }

    fun getCompetitorCategories(competitionId: String, fighterId: String): List<String> =
            create.selectFrom(CategoryDescriptor.CATEGORY_DESCRIPTOR.join(CompetitorCategories.COMPETITOR_CATEGORIES)
                    .on(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(CompetitorCategories.COMPETITOR_CATEGORIES.CATEGORIES_ID)))
                    .where(CompetitorCategories.COMPETITOR_CATEGORIES.COMPETITORS_ID.equal(fighterId)).and(CategoryDescriptor.CATEGORY_DESCRIPTOR.COMPETITION_ID.equal(competitionId))
                    .fetch(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID, String::class.java).orEmpty()




    fun topMatFights(limit: Int = 100, competitionId: String, matId: String, statuses: Iterable<FightStatus>): Flux<FightDescriptionDTO> {
        val queryResultsFlux =
                Flux.from(queryProvider.topMatFightsQuery(limit, competitionId, matId, statuses))
        return fightsMapping(queryResultsFlux)
    }

    fun fightsMapping(queryResultsFlux: Flux<Record>, filterEmptyFights: Boolean = false): Flux<FightDescriptionDTO> = queryResultsFlux.groupBy { rec -> rec[FightDescription.FIGHT_DESCRIPTION.ID] }
            .flatMap { fl ->
                fl.collect(jooqMappers.fightCollector())
            }.filter { f ->
                !filterEmptyFights
                        || (f.scores?.size == 2 && (f.scores?.all { it.competitorId != null } == true))
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
                invalid = it.invalid
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
                    }.orEmpty())
        }
        create.batchStore(records).execute()
    }

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
                            .and(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.eq(cs.competitorId))
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
                invalid = fight.invalid
            }

    private fun compscoreRecord(cs: CompScoreDTO, fightId: String) =
            CompScoreRecord().apply {
                advantages = cs.score?.advantages
                points = cs.score?.points
                penalties = cs.score?.penalties
                compscoreFightDescriptionId = fightId
                compScoreOrder = cs.order
                compscoreCompetitorId = cs.competitorId
                placeholderId = cs.placeholderId
            }


    fun dropStages(categoryId: String) {
        create.deleteFrom(StageDescriptor.STAGE_DESCRIPTOR).where(StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID.eq(categoryId)).execute()
    }

    fun fetchStageById(competitionId: String, stageId: String): Mono<StageDescriptorDTO> = Flux.from(
            queryProvider.selectStagesByIdQuery(competitionId, stageId)
    ).groupBy { it[StageDescriptor.STAGE_DESCRIPTOR.ID] }
            .flatMap { records ->
                flatMapToTupleMono(records)
            }.reduce { t, u -> t ?: u }

    private fun flatMapToTupleMono(records: GroupedFlux<String, Record>): Mono<StageDescriptorDTO> {
        return records.collect(jooqMappers.stageCollector())
    }

    fun fetchStagesForCategory(competitionId: String, categoryId: String): Flux<StageDescriptorDTO> = Flux.from(
            queryProvider.selectStagesByCategoryIdQuery(competitionId, categoryId)
    ).groupBy { it[StageDescriptor.STAGE_DESCRIPTOR.ID] }
            .flatMap { records ->
                flatMapToTupleMono(records)
            }


    fun saveCompetitors(comps: List<CompetitorDTO>): IntArray =
            create.batchInsert(comps.map { comp ->
                CompetitorRecord().also { cmp ->
                    cmp.id = comp.id
                    cmp.academyId = comp.academy?.id
                    cmp.academyName = comp.academy?.name
                    cmp.birthDate = comp.birthDate?.let { Timestamp.from(it) }
                    cmp.email = comp.email
                    cmp.firstName = comp.firstName
                    cmp.lastName = comp.lastName
                    cmp.competitionId = comp.competitionId
                    cmp.promo = comp.promo
                    cmp.registrationStatus = comp.registrationStatus?.let { RegistrationStatus.valueOf(it).ordinal }
                    cmp.userId = comp.userId
                }
            }).execute()

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

    fun doInTransaction(f: (c: Configuration) -> Unit) {
        create.transaction { configuration -> f.invoke(configuration) }
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
                                }.orEmpty())
                    }.orEmpty())
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
            val k1 = pair.second.competitorResults?.map { cr ->
                saveCompetitorResultQuery(cr)
            }.orEmpty()
            val k2 = pair.second.fightResultOptions?.map {
                queryProvider.saveFightResultOptionQuery(pair.first, it)
            }.orEmpty()
            val k3 = pair.second.additionalGroupSortingDescriptors?.map {
                queryProvider.saveAdditionalGroupSortingDescriptorQuery(pair.first, it)
            }.orEmpty()
            k1 + k2 + k3
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
                create.deleteFrom(CompScore.COMP_SCORE)
                        .where(CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID.eq(fightId))
                        .and(CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID.eq(value.competitorId)),
                create.insertInto(CompScore.COMP_SCORE,
                        CompScore.COMP_SCORE.COMPSCORE_COMPETITOR_ID,
                        CompScore.COMP_SCORE.COMPSCORE_FIGHT_DESCRIPTION_ID,
                        CompScore.COMP_SCORE.POINTS,
                        CompScore.COMP_SCORE.PENALTIES,
                        CompScore.COMP_SCORE.ADVANTAGES,
                        CompScore.COMP_SCORE.COMP_SCORE_ORDER,
                        CompScore.COMP_SCORE.PLACEHOLDER_ID).values(value.competitorId, fightId, value.score.points, value.score.penalties,
                        value.score.advantages, index, value.placeholderId)
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
                        .orEmpty(), per.id)
        ).execute()
    }

    fun saveCompetitionState(state: CompetitionStateDTO?) {
        state?.let { compState ->
            create.batch(
                    listOf(queryProvider.saveCompetitionPropertiesQuery(compState.properties)) +
                            (state.properties.staffIds?.map {
                                create.insertInto(CompetitionPropertiesStaffIds.COMPETITION_PROPERTIES_STAFF_IDS,
                                        CompetitionPropertiesStaffIds.COMPETITION_PROPERTIES_STAFF_IDS.COMPETITION_PROPERTIES_ID,
                                        CompetitionPropertiesStaffIds.COMPETITION_PROPERTIES_STAFF_IDS.STAFF_ID)
                                        .values(state.properties.id, it)
                            }.orEmpty()) +
                            (state.properties.promoCodes?.map { promo ->
                                create.insertInto(PromoCode.PROMO_CODE,
                                        PromoCode.PROMO_CODE.ID,
                                        PromoCode.PROMO_CODE.COEFFICIENT,
                                        PromoCode.PROMO_CODE.COMPETITION_ID,
                                        PromoCode.PROMO_CODE.EXPIRE_AT,
                                        PromoCode.PROMO_CODE.START_AT)
                                        .values(promo.id, promo.coefficient, promo.competitionId, promo.expireAt?.toTimestamp(), promo.startAt?.toTimestamp())
                            }.orEmpty())
            ).execute()
        }
    }

    fun deleteScheduleEntriesByCompetitionId(competitionId: String) {
        val periodIds = create.select(SchedulePeriod.SCHEDULE_PERIOD.ID)
                .from(SchedulePeriod.SCHEDULE_PERIOD).where(SchedulePeriod.SCHEDULE_PERIOD.COMPETITION_ID.eq(competitionId)).fetchSet { it.value1() }.orEmpty()
        if (!periodIds.isNullOrEmpty()) {
            val entryIds = create.select(ScheduleEntry.SCHEDULE_ENTRY.ID)
                    .from(ScheduleEntry.SCHEDULE_ENTRY).where(ScheduleEntry.SCHEDULE_ENTRY.PERIOD_ID.`in`(periodIds))
                    .fetchSet { it.value1() }.orEmpty()
            create.batch(
                    listOf(create.update(FightDescription.FIGHT_DESCRIPTION)
                            .set(FightDescription.FIGHT_DESCRIPTION.PERIOD, null as String?)
                            .set(FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID, null as String?)
                            .set(FightDescription.FIGHT_DESCRIPTION.MAT_ID, null as String?)
                            .set(FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT, null as Int?)
                            .set(FightDescription.FIGHT_DESCRIPTION.START_TIME, null as Timestamp?)
                            .set(FightDescription.FIGHT_DESCRIPTION.INVALID, false)
                            .where(FightDescription.FIGHT_DESCRIPTION.COMPETITION_ID.eq(competitionId))

                            , create.deleteFrom(ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT)
                            .where(ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT.SCHEDULE_ENTRY_ID.`in`(entryIds)),
                            create.deleteFrom(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY)
                                    .where(CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID.`in`(entryIds)),
                            create.deleteFrom(ScheduleEntry.SCHEDULE_ENTRY)
                                    .where(ScheduleEntry.SCHEDULE_ENTRY.PERIOD_ID.`in`(periodIds)))
            ).execute()
        }
    }

    fun saveSchedule(schedule: ScheduleDTO?) = schedule?.let {
        create.batch(queryProvider.saveScheduleQuery(it)).execute()
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
    }.flatMap { per ->
        addScheduleRequirementsToScheduleEntries(per)
    }.flatMap { per ->
        addCategoriesToScheduleRequirements(per)
    }.flatMap { per -> addFightIdsToScheduleRequirements(per) }

    private fun addFightIdsToScheduleRequirements(per: PeriodDTO): Mono<PeriodDTO> {
        return Flux.from(
                create.selectFrom(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION)
                        .where(ScheduleRequirementFightDescription.SCHEDULE_REQUIREMENT_FIGHT_DESCRIPTION.REQUIREMENT_ID.`in`(per.scheduleRequirements?.map { it.id }.orEmpty()))
        ).collectList()
                .map { list ->
                    val map = list.groupBy { it.requirementId }.mapValues { e -> e.value.map { it.fightId } }
                    per.setScheduleRequirements(per.scheduleRequirements?.map { it.setFightIds(map[it.id]?.toTypedArray().orEmpty()) }?.toTypedArray())
                }
    }

    private fun addCategoriesToScheduleRequirements(per: PeriodDTO): Mono<PeriodDTO> {
        return Flux.from(
                create.selectFrom(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION)
                        .where(ScheduleRequirementCategoryDescription.SCHEDULE_REQUIREMENT_CATEGORY_DESCRIPTION.REQUIREMENT_ID.`in`(per.scheduleRequirements?.map { it.id }.orEmpty()))
        ).collectList()
                .map { list ->
                    val map = list.groupBy { it.requirementId }.mapValues { e -> e.value.map { it.categoryId } }
                    per.setScheduleRequirements(per.scheduleRequirements?.map { it.setCategoryIds(map[it.id]?.toTypedArray().orEmpty()) }?.toTypedArray())
                }
    }

    private fun addScheduleRequirementsToScheduleEntries(per: PeriodDTO): Mono<PeriodDTO> {
        return Flux.from(
                create.selectFrom(ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT)
                        .where(ScheduleEntryScheduleRequirement.SCHEDULE_ENTRY_SCHEDULE_REQUIREMENT.SCHEDULE_ENTRY_ID.`in`(per.scheduleEntries.map { it.id }))
        )
                .collectList()
                .map { list ->
                    val map = list.groupBy { it.scheduleEntryId }
                            .mapValues { e ->
                                e.value.map { it.scheduleRequirementId }
                            }
                    per.setScheduleEntries(per.scheduleEntries?.map { it.setRequirementIds(map[it.id]?.toTypedArray()) }?.toTypedArray())
                }
    }

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