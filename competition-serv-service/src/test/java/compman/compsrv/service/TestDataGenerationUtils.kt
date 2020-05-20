package compman.compsrv.service

import arrow.core.Tuple2
import arrow.core.Tuple3
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.GroupStageGenerateService
import compman.compsrv.service.schedule.BracketSimulatorFactory
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import compman.compsrv.util.IDGenerator
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestDataGenerationUtils(private val bracketsGenerateService: BracketsGenerateService, private val groupStageGenerateService: GroupStageGenerateService) {
    private val scheduleService = ScheduleService()

    fun category1(fightDuration: Long) = CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
    fun category2(fightDuration: Long) = CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.white)
    fun category3(fightDuration: Long) = CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.black)
    fun category4(fightDuration: Long) = CategoryGeneratorService.createCategory(CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.blue)


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
                .setCreationTimestamp(System.currentTimeMillis())
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
                    .setName("Test Period 1")
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
        val mats1 = arrayOf(MatDescriptionDTO()
                .setId("mat1")
                .setMatOrder(0)
                .setName("Mat 1")
                .setPeriodId(period1))

        val mats2 = arrayOf(MatDescriptionDTO()
                .setId("mat2")
                .setMatOrder(0)
                .setName("Mat 1 Period2")
                .setPeriodId(period2),
                MatDescriptionDTO()
                        .setId("mat3")
                        .setMatOrder(1)
                        .setName("Mat 2 Period2")
                        .setPeriodId(period2))

        val mats = mats1 + mats2

        val lastCatFights = findFightIdsByCatIds.invoke(categories.subList(2, categories.size).map { it.second.id })
        val periods = listOf(createPeriod(period1, arrayOf(
                ScheduleRequirementDTO()
                        .setId("$period1-entry1")
                        .setCategoryIds(categories.subList(0, 1).map { it.second.id }
                                .toTypedArray())
                        .setMatId("mat1")
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
                                .setMatId("mat2")
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
        val getCompScores = { id: String -> stagesToFights.flatMap { it.second }.firstOrNull { it.id == id }?.scores?.map { it.toPojo(id) }.orEmpty() }

        val stageGraph = stagesToFights.map {
            Tuple3(it.first, it.second.first().categoryId, BracketType.SINGLE_ELIMINATION) to it.second.map { f ->
                f.toPojo()
            }
        }.map { pp ->
            StageGraph(pp.first.a.categoryId, listOf(pp.first.a), pp.second, BracketSimulatorFactory(), getCompScores)
        }

        return scheduleService.generateSchedule(competitionId, periods, mats.toList(), Flux.fromIterable(stageGraph), TimeZone.getDefault().id, categories.map { it.second.id to competitorNumbers }.toMap())
    }
}