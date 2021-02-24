package compman.compsrv.service

import arrow.core.Tuple2
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.GroupStageGenerateService
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import compman.compsrv.util.IDGenerator
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestDataGenerationUtils(private val bracketsGenerateService: BracketsGenerateService, private val groupStageGenerateService: GroupStageGenerateService) {
    private val scheduleService = ScheduleService()

    fun category1() = CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj,
            CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
    fun category2() =
            CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.white)
    fun category3() =
            CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.black)
    fun category4() =
            CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.blue)


    fun generateFilledFights(competitionId: String,
                             category: CategoryDescriptorDTO,
                             stage: StageDescriptorDTO,
                             competitors: List<CompetitorDTO>,
                             duration: BigDecimal): List<FightDescriptionDTO> {
        val fights = bracketsGenerateService.generateStageFights(competitionId, category.id, stage, competitors.size, duration, competitors, 0)
        assertNotNull(fights)
        return fights
    }

    fun generateGroupFights(competitionId: String,
                                    categoryId: String,
                                    stageId: String,
                                    additionalGroupSortingDescriptors: Array<AdditionalGroupSortingDescriptorDTO>?,
                                    groupSizes: List<Int>): Pair<StageDescriptorDTO, List<FightDescriptionDTO>> {
        println("Generating group fights.")
        val duration = BigDecimal.TEN
        val competitorsSize = groupSizes.sum()
        val stage = createGroupStage(competitionId, categoryId, stageId, additionalGroupSortingDescriptors, groupSizes)
        val competitors = FightsService.generateRandomCompetitorsForCategory(competitorsSize, 20, categoryId, competitionId)
        val fights = groupStageGenerateService.generateStageFights(competitionId, categoryId, stage, competitorsSize, duration, competitors, 0)
        assertNotNull(fights)
        assertTrue(fights.all { it.scores.size == 2 }) //all fights are packed
        assertTrue(fights.none { it.groupId.isNullOrBlank() }) //all fights have a group id
        assertTrue(competitors.all { comp -> fights.filter { f -> f.scores.any { it.competitorId == comp.id } }.size == competitorsSize - 1 }) //each fighter fights with all the other fighters
        return stage to fights
    }


    fun createGroupStage(competitionId: String,
                         categoryId: String,
                         stageId: String,
                         additionalGroupSortingDescriptorDTOS: Array<AdditionalGroupSortingDescriptorDTO>?,
    groupSizes: List<Int>): StageDescriptorDTO {
        return StageDescriptorDTO()
                .setId(stageId)
                .setName("Name")
                .setBracketType(BracketType.GROUP)
                .setStageType(StageType.FINAL)
                .setCategoryId(categoryId)
                .setCompetitionId(competitionId)
                .setHasThirdPlaceFight(false)
                .setStageOrder(0)
                .setFightDuration(BigDecimal.TEN)
                .setStageStatus(StageStatus.APPROVED)
                .setStageResultDescriptor(StageResultDescriptorDTO()
                        .setId(stageId)
                        .setAdditionalGroupSortingDescriptors(additionalGroupSortingDescriptorDTOS))
                .setGroupDescriptors(groupSizes.mapIndexed {ind, size ->
                    GroupDescriptorDTO()
                            .setId(stageId + "-group-" + IDGenerator.uid())
                            .setName(stageId + "group-Name-$ind")
                            .setSize(size)
                }.toTypedArray())
                .setInputDescriptor(StageInputDescriptorDTO().setId(stageId).setNumberOfCompetitors(50))
    }

    fun createSingleEliminationStage(competitionId: String,
                                     categoryId: String,
                                     stageId: String,
                                     numberOfCompetitors: Int): StageDescriptorDTO {
        return StageDescriptorDTO()
                .setId(stageId)
                .setName("Name")
                .setBracketType(BracketType.SINGLE_ELIMINATION)
                .setStageType(StageType.FINAL)
                .setCategoryId(categoryId)
                .setCompetitionId(competitionId)
                .setHasThirdPlaceFight(false)
                .setFightDuration(BigDecimal.TEN)
                .setStageOrder(0)
                .setStageStatus(StageStatus.APPROVED)
                .setStageResultDescriptor(StageResultDescriptorDTO()
                        .setFightResultOptions(FightResultOptionDTO.values.toTypedArray())
                        .setId(stageId))
                .setInputDescriptor(StageInputDescriptorDTO().setId(stageId).setNumberOfCompetitors(numberOfCompetitors))
    }

    fun createCompetitionPropertiesDTO(competitionId: String?): CompetitionPropertiesDTO {
        return CompetitionPropertiesDTO()
                .setCompetitionName("Compname")
                .setId(competitionId)
                .setBracketsPublished(false)
                .setCreationTimestamp(Instant.now())
                .setCreatorId("creatorId")
                .setEmailNotificationsEnabled(false)
                .setEmailTemplate("")
                .setEndDate(Instant.now())
                .setStartDate(Instant.now())
                .setStatus(CompetitionStatus.CREATED)
                .setTimeZone("UTC")
                .setSchedulePublished(false)
    }

    private fun createPeriod(id: String, scheduleEntries: Array<ScheduleRequirementDTO>): PeriodDTO =
            PeriodDTO()
                    .setId(id)
                    .setRiskPercent(BigDecimal("0.1"))
                    .setTimeBetweenFights(1)
                    .setStartTime(Instant.now())
                    .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                    .setName("Test $id")
                    .setScheduleRequirements(scheduleEntries)

    fun generateSchedule(categories: List<Pair<String, CategoryDescriptorDTO>>,
                         stagesToFights: List<Pair<StageDescriptorDTO, List<FightDescriptionDTO>>>,
                         competitionId: String,
                         competitorNumbers: Int): Tuple2<ScheduleDTO, List<FightStartTimePairDTO>> {
        val findFightIdsByCatIds = { categoryIds: Collection<String> ->
            stagesToFights.flatMap { it.second }.filter { categoryIds.contains(it.categoryId) }.map { it.id }
        }
        val period1 = "Period1"
        val period2 = "Period2"
        val mats = createDefaultMats(period1, period2)

        val periods = createDefaultPeriods(findFightIdsByCatIds, categories, period1, period2)
        val stages = stagesToFights.map { it.first }
        val fights = stagesToFights.flatMap { it.second }

        return scheduleService.generateSchedule(competitionId, periods, mats.toList(), Mono.just(StageGraph(stages, fights)), TimeZone.getDefault().id, categories.map { it.second.id to competitorNumbers }.toMap())
    }

    fun generateSchedule(competitionId: String, periods: List<PeriodDTO>, mats: List<MatDescriptionDTO>, stages: List<StageDescriptorDTO>, fights: List<FightDescriptionDTO>,
                         categories: List<Pair<String, CategoryDescriptorDTO>>, competitorNumbers: Int) = scheduleService.generateSchedule(competitionId, periods, mats,
            Mono.just(StageGraph(stages, fights)), TimeZone.getDefault().id, categories.map { it.second.id to competitorNumbers }.toMap())

    fun createDefaultMats(period1: String, period2: String): Array<MatDescriptionDTO> {
        val mats1 = arrayOf(MatDescriptionDTO()
                .setId("$period1-mat1")
                .setMatOrder(0)
                .setName("Mat 1")
                .setPeriodId(period1))

        val mats2 = arrayOf(MatDescriptionDTO()
                .setId("$period2-mat2")
                .setMatOrder(0)
                .setName("Mat 1 Period2")
                .setPeriodId(period2),
                MatDescriptionDTO()
                        .setId("$period2-mat3")
                        .setMatOrder(1)
                        .setName("Mat 2 Period2")
                        .setPeriodId(period2))

        return mats1 + mats2
    }

    fun createDefaultPeriods(findFightIdsByCatIds: (Collection<String>) -> List<String>, categories: List<Pair<String, CategoryDescriptorDTO>>, period1: String, period2: String): List<PeriodDTO> {
        val lastCatFights = findFightIdsByCatIds(categories.subList(2, categories.size).map { it.second.id }).shuffled().take(10)
        return listOf(createPeriod(period1, arrayOf(
                ScheduleRequirementDTO()
                        .setId("$period1-entry1")
                        .setCategoryIds(categories.subList(0, 1).map { it.second.id }
                                .toTypedArray())
                        .setMatId("$period1-mat1")
                        .setEntryType(ScheduleRequirementType.CATEGORIES)
                        .setEntryOrder(0)
        )),
                createPeriod(period2, arrayOf(
                        ScheduleRequirementDTO()
                                .setId("$period2-entry1")
                                .setCategoryIds(categories.subList(1, 2).map { it.second.id }
                                        .toTypedArray())
                                .setEntryType(ScheduleRequirementType.CATEGORIES)
                                .setEntryOrder(0),
                        ScheduleRequirementDTO()
                                .setId("$period2-entry2")
                                .setMatId("$period2-mat2")
                                .setFightIds(
                                        lastCatFights.take(5).toTypedArray()
                                )
                                .setEntryType(ScheduleRequirementType.FIGHTS)
                                .setEntryOrder(1),
                        ScheduleRequirementDTO()
                                .setId("$period2-entry3")
                                .setCategoryIds(categories.subList(2, categories.size).map { it.second.id }.toTypedArray())
                                .setEntryType(ScheduleRequirementType.CATEGORIES)
                                .setEntryOrder(2)

                )))
    }
}