package compman.compsrv.service

import arrow.core.Tuple3
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.dto.schedule.ScheduleRequirementDTO
import compman.compsrv.model.dto.schedule.ScheduleRequirementType
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.schedule.BracketSimulatorFactory
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNotNull

class TestDataGenerationUtils(private val fightsGenerateService: FightsService) {
    private val scheduleService = ScheduleService()

    fun category1(fightDuration: Long) = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
    fun category2(fightDuration: Long) = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.white)
    fun category3(fightDuration: Long) = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.black)
    fun category4(fightDuration: Long) = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.blue)


    fun generateFilledFights(competitionId: String,
                             category: CategoryDescriptorDTO,
                             stage: StageDescriptorDTO,
                             competitors: List<CompetitorDTO>,
                             duration: BigDecimal): List<FightDescriptionDTO> {
        val fights = fightsGenerateService.generateStageFights(competitionId, category.id, stage, competitors.size, duration, competitors, 0)
        assertNotNull(fights)
        return fights
    }

    fun createGroupStage(competitionId: String,
                         categoryId: String,
                         stageId: String,
                         additionalGroupSortingDescriptorDTOS: Array<AdditionalGroupSortingDescriptorDTO>?): StageDescriptorDTO {
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
                .setGroupDescriptors(arrayOf(
                        GroupDescriptorDTO()
                                .setId(stageId + "-group-" + UUID.randomUUID().toString())
                                .setName(stageId + "group-Name")
                                .setSize(25),
                        GroupDescriptorDTO()
                                .setId(stageId + "-group-" + UUID.randomUUID().toString())
                                .setName(stageId + "group-Name1")
                                .setSize(25)
                ))
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
                         competitorNumbers: Int): ScheduleDTO {
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

        return scheduleService.generateSchedule(competitionId, periods, mats.toList(), Flux.fromIterable(stageGraph), TimeZone.getDefault().id, categories.map { it.second.id to competitorNumbers }.toMap(), getCompScores)
    }
}