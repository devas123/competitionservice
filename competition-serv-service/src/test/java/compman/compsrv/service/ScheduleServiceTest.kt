package compman.compsrv.service

import arrow.core.Tuple3
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.brackets.StageType
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.schedule.BracketSimulatorFactory
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleServiceTest {
    private val fightsGenerateService = BracketsGenerateService()
    private val fightDuration = 8L

    private fun category1() = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
    private fun category2() = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.white)
    private fun category3() = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.black)
    private fun category4() = CategoryGeneratorService.createCategory(fightDuration, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.blue)

    private fun generateFilledFights(competitionId: String, category: CategoryDescriptorDTO, stageId: String, compsSize: Int, duration: BigDecimal): List<FightDescriptionDTO> {
        val stage = StageDescriptorDTO()
                .setId(stageId)
                .setStageOrder(0)
                .setStageType(StageType.FINAL)
                .setStageStatus(StageStatus.APPROVED)
                .setBracketType(BracketType.SINGLE_ELIMINATION)
                .setHasThirdPlaceFight(false)
                .setCategoryId(category.id)
        val competitors = FightsService.generateRandomCompetitorsForCategory(compsSize, 20, category, competitionId)
        val fights = fightsGenerateService.generateStageFights(competitionId, category.id, stage, compsSize, duration, competitors, 0)
        assertNotNull(fights)
        return fights
    }

    @Test
    fun testScheduleGeneration() {
        val competitionId = "competitionId"
        val categories = listOf("stageid1" to category1(), "stageid2" to category2(), "stageid3" to category3(), "stageid4" to category4())
        val competitorNumbers = 10
        val fights = categories.map {
            it.first to generateFilledFights(competitionId, it.second, it.first, competitorNumbers, BigDecimal.valueOf(fightDuration))
        }.map { dto -> dto.copy(second = dto.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo()) })  }
        val flatFights = fights.flatMap { it.second }.filter { f -> !ScheduleService.obsoleteFight(f.toPojo()) }
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

        val findFightIdsByCatIds = { categoryIds: Collection<String> ->
            fights.flatMap { it.second }.filter { categoryIds.contains(it.categoryId) }.map { it.id }
        }

        val scheduleService = ScheduleService(BracketSimulatorFactory())
        val lastCatFights = findFightIdsByCatIds.invoke(categories.subList(2, categories.size).map { it.second.id })
        val periods = listOf(createPeriod(period1, mats1, arrayOf(
                ScheduleRequirementDTO()
                        .setId("$period1-entry1")
                        .setCategoryIds(categories.subList(0, 1).map { it.second.id }
                                .toTypedArray())
                        .setMatId("mat1")
                        .setEntryType(ScheduleRequirementType.CATEGORIES)
        )),
                createPeriod(period2, mats2, arrayOf(
                        ScheduleRequirementDTO()
                                .setId("$period2-entry1")
                                .setCategoryIds(categories.subList(1, 2).map { it.second.id }
                                        .toTypedArray())
                                .setEntryType(ScheduleRequirementType.CATEGORIES),
                        ScheduleRequirementDTO()
                                .setId("$period2-entry2")
                                .setMatId("mat2")
                                .setFightIds(
                                        lastCatFights.take(5).toTypedArray()
                                )
                                .setEntryType(ScheduleRequirementType.FIGHTS),
                        ScheduleRequirementDTO()
                                .setId("$period2-entry3")
                                .setCategoryIds(categories.subList(2, categories.size).map { it.second.id }.toTypedArray())
                                .setEntryType(ScheduleRequirementType.CATEGORIES)

                )))
        val schedule = scheduleService.generateSchedule(competitionId, periods, Flux.fromIterable(fights.map {
            Tuple3(it.first, it.second.first().categoryId, BracketType.SINGLE_ELIMINATION) to it.second.map { f ->
                f.toPojo()
            }
        }), TimeZone.getDefault().id, categories.map { it.second.id to competitorNumbers }.toMap())

        println("Fights: ")
        fights.forEach {
            println("${it.first} -> ${it.second.filter{ dto -> !ScheduleService.obsoleteFight(dto.toPojo()) }.size}")
        }

        assertNotNull(schedule)
        assertNotNull(schedule.periods)
        assertEquals(flatFights.size, schedule.periods.flatMap { it.mats.flatMap { descriptionDTO -> descriptionDTO.fightStartTimes.toList() } }.size)
        assertEquals(flatFights.size, schedule.periods.flatMap { it.scheduleEntries.flatMap { entryDTO -> entryDTO.fightIds.toList() } }.size)
        val fightStartTimes = schedule.periods.flatMap { it.mats.flatMap { dto -> dto.fightStartTimes.toList() } }
        val fightIdsInSchedule = schedule.periods.flatMap { it.scheduleEntries.flatMap { dto -> dto.fightIds.toList() } }
        assertTrue(flatFights.fold(true) { acc, f -> acc && fightStartTimes.any { it.fightId == f.id } })
        assertTrue(flatFights.fold(true) { acc, f -> acc && fightIdsInSchedule.contains(f.id) })
        assertEquals(categories.size, schedule.periods.flatMap { it.scheduleEntries.flatMap { scheduleEntryDTO -> scheduleEntryDTO.categoryIds.toList() } }.distinct().size)
        println("Periods: ")
        schedule.periods.forEach {
            println("\n==== \n==== \n====")
            println("${it.id} -> ${it.name}")
            println("------------------ MATS: ${it.mats?.size} --------------")
            it.mats.forEach { mat ->
                println("${mat.id} -> ${mat.fightStartTimes?.size} -> \n${mat.fightStartTimes?.joinToString(separator = "\n") { f -> "${f.fightId} -> ${f.startTime} -> ${f.numberOnMat}" }}")
            }
            println("------------------ SCHEDULE REQUIREMENTS ${it.scheduleRequirements?.size} -----------------")
            it.scheduleRequirements?.forEach {e ->
                println("${e.id} / start = ${e.startTime} / end = ${e.endTime} / categories: ${e.categoryIds?.distinct()?.size} / mat = ${e.matId} / fights: ${e.fightIds?.distinct()?.joinToString("\n")}")
            }
            println("------------------ SCHEDULE ENTRIES ${it.scheduleEntries?.size} -----------------")
            it.scheduleEntries.forEach {e ->
                println("${e.id} /  start =  ${e.startTime} / end =  ${e.endTime} / categories: ${e.categoryIds?.distinct()?.size}  / mat = ${e.matId} / fights: ${e.fightIds?.distinct()?.size}")
            }
        }
    }


    private fun createPeriod(id: String, mats: Array<MatDescriptionDTO>, scheduleEntries: Array<ScheduleRequirementDTO>): PeriodDTO =
            PeriodDTO()
                    .setId(id)
                    .setMats(mats)
                    .setRiskPercent(BigDecimal("0.1"))
                    .setTimeBetweenFights(1)
                    .setStartTime(Instant.now())
                    .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                    .setName("Test Period 1")
                    .setScheduleRequirements(scheduleEntries)
}
