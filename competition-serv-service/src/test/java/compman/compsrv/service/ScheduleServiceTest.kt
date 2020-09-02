package compman.compsrv.service

import arrow.core.Tuple2
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.GroupStageGenerateService
import compman.compsrv.service.schedule.ScheduleService
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleServiceTest {
    private val fightsGenerateService = BracketsGenerateService()
    private val groupFightsGenerateService = GroupStageGenerateService()
    private val fightDuration = 8L


    private val testBracketsDataGenerationUtils = TestDataGenerationUtils(fightsGenerateService, groupFightsGenerateService)

    private fun getMatIds(e: ScheduleEntryDTO): List<String> = e.fightIds.map { it.matId }.distinct()

    @Test
    fun testRelativePause() {
        val competitionId = "competitionId"
        val categories = listOf("stageid1" to testBracketsDataGenerationUtils.category1(),
                "stageid2" to testBracketsDataGenerationUtils.category2(),
                "stageid3" to testBracketsDataGenerationUtils.category3(),
                "stageid4" to testBracketsDataGenerationUtils.category4())
        val competitorNumbers = 10
        val stagesToFights = categories.map {
            val competitors = FightsService.generateRandomCompetitorsForCategory(competitorNumbers, competitorNumbers, it.second.id, competitionId)
            val stage = testBracketsDataGenerationUtils.createSingleEliminationStage(competitionId, it.second.id, it.first, competitorNumbers)
            stage to testBracketsDataGenerationUtils.generateFilledFights(competitionId, it.second, stage, competitors, BigDecimal.valueOf(fightDuration))
        }.map { dto -> dto.copy(second = dto.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }) }
        val flatFights = stagesToFights.flatMap { it.second }.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }

        val period1 = "P1"
        val period2 = "P2"

        val periods = testBracketsDataGenerationUtils.createDefaultPeriods({ categoryIds -> flatFights.filter { f -> categoryIds.contains(f.categoryId) }.map { f -> f.id }}, categories, period1, period2)
        val lastIndex = periods[1].scheduleRequirements.size - 1
        periods[1].scheduleRequirements = periods[1].scheduleRequirements + ScheduleRequirementDTO()
                .setId("$period2-entryPause")
                .setMatId("mat1")
                .setEntryType(ScheduleRequirementType.RELATIVE_PAUSE)
                .setDurationMinutes(BigDecimal(10))
                .setEntryOrder(lastIndex - 1)
        val tmp = periods[1].scheduleRequirements[lastIndex + 1]
        periods[1].scheduleRequirements[lastIndex + 1] = periods[1].scheduleRequirements[lastIndex]
        periods[1].scheduleRequirements[lastIndex] = tmp
        periods[1].scheduleRequirements.forEachIndexed { index, scheduleRequirementDTO -> scheduleRequirementDTO.entryOrder = index }
        val stages = stagesToFights.map { it.first }
        val fights = stagesToFights.flatMap { it.second }.map { it.toPojo() }
        val mats = testBracketsDataGenerationUtils.createDefaultMats(period1, period2)
        val tuple2 = testBracketsDataGenerationUtils.generateSchedule(competitionId, periods, mats.toList(), stages, fights, categories, competitorNumbers)
        val (schedule, fstms) = printSchedule(tuple2, stagesToFights)
        assertNotNull(schedule.periods)
        assertNotNull(schedule.mats)
        assertEquals(3, schedule.mats.size)
        assertEquals(2, schedule.periods.size)
        schedule.periods.forEach { p -> assertNotNull(p.scheduleEntries) }
        assertTrue(schedule.periods[1].scheduleEntries.any { it.entryType == ScheduleEntryType.RELATIVE_PAUSE })
    }

    @Test
    fun testInvalidOrder() {
        val competitionId = "competitionId"
        val categories = listOf("stageid1" to testBracketsDataGenerationUtils.category1(),
                "stageid2" to testBracketsDataGenerationUtils.category2(),
                "stageid3" to testBracketsDataGenerationUtils.category3(),
                "stageid4" to testBracketsDataGenerationUtils.category4())
        val competitorNumbers = 110
        val stagesToFights = categories.map {
            val competitors = FightsService.generateRandomCompetitorsForCategory(competitorNumbers, competitorNumbers, it.second.id, competitionId)
            val stage = testBracketsDataGenerationUtils.createSingleEliminationStage(competitionId, it.second.id, it.first, competitorNumbers)
            stage to testBracketsDataGenerationUtils.generateFilledFights(competitionId, it.second, stage, competitors, BigDecimal.valueOf(fightDuration))
        }.map { dto -> dto.copy(second = dto.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }) }
        val flatFights = stagesToFights.flatMap { it.second }.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }

        val period1 = "P1"
        val period2 = "P2"

        val periods = testBracketsDataGenerationUtils.createDefaultPeriods({ categoryIds -> flatFights.filter { f -> categoryIds.contains(f.categoryId) }.map { f -> f.id }}, categories, period1, period2)
        periods[0].scheduleRequirements = periods[0].scheduleRequirements + periods[1].scheduleRequirements[1]
        periods[1].scheduleRequirements = periods[1].scheduleRequirements.copyOfRange(0, 1) + periods[1].scheduleRequirements[2]
        periods[1].scheduleRequirements.forEachIndexed { index, scheduleRequirementDTO ->
            scheduleRequirementDTO.entryOrder = index
            scheduleRequirementDTO.id = "Period2-req-$index"
        }
        periods[0].scheduleRequirements.forEachIndexed { index, scheduleRequirementDTO ->
            scheduleRequirementDTO.entryOrder = index
            scheduleRequirementDTO.id = "Period1-req-$index"
        }
        val stages = stagesToFights.map { it.first }
        val fights = stagesToFights.flatMap { it.second }.map { it.toPojo() }
        val mats = testBracketsDataGenerationUtils.createDefaultMats(period1, period2)
        val tuple2 = testBracketsDataGenerationUtils.generateSchedule(competitionId, periods, mats.toList(), stages, fights, categories, competitorNumbers)
        val (schedule, fstms) = printSchedule(tuple2, stagesToFights)
        assertNotNull(schedule.periods)
        assertNotNull(schedule.mats)
        assertEquals(3, schedule.mats.size)
        assertEquals(2, schedule.periods.size)
        schedule.periods.forEach { p -> assertNotNull(p.scheduleEntries) }
        assertTrue(schedule.periods[1].scheduleEntries.any { p -> !p.invalidFightIds.isNullOrEmpty() })
    }

    @Test
    fun testScheduleGeneration() {
        val competitionId = "competitionId"
        val categories = listOf("stageid1" to testBracketsDataGenerationUtils.category1(),
                "stageid2" to testBracketsDataGenerationUtils.category2(),
                "stageid3" to testBracketsDataGenerationUtils.category3(),
                "stageid4" to testBracketsDataGenerationUtils.category4())
        val competitorNumbers = 10
        val fights = categories.map {
            val competitors = FightsService.generateRandomCompetitorsForCategory(competitorNumbers, competitorNumbers, it.second.id, competitionId)
            val stage = testBracketsDataGenerationUtils.createSingleEliminationStage(competitionId, it.second.id, it.first, competitorNumbers)
            stage to testBracketsDataGenerationUtils.generateFilledFights(competitionId, it.second, stage, competitors, BigDecimal.valueOf(fightDuration))
        }.map { dto -> dto.copy(second = dto.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }) }
        val flatFights = fights.flatMap { it.second }.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }

        val tuple = testBracketsDataGenerationUtils.generateSchedule(categories, fights, competitionId, competitorNumbers)
        val (schedule, fstms) = printSchedule(tuple, fights)

        assertNotNull(schedule)
        assertNotNull(schedule.periods)
        assertEquals(flatFights.size, fstms.size, "Following fights were not dispatched: \n${flatFights.filter { ff -> fstms.none { fst -> fst.fightId == ff.id } }.joinToString("\n")}")
        assertTrue(flatFights.fold(true) { acc, f -> acc && fstms.any { it.fightId == f.id } })
        assertEquals(categories.size, schedule.periods.flatMap { it.scheduleEntries.flatMap { scheduleEntryDTO -> scheduleEntryDTO.categoryIds.toList() } }.distinct().size)
        assertTrue(schedule.periods.all {
            it.scheduleRequirements.all { sr ->
                sr.entryOrder != null && sr.periodId == it.id
            }
        })
        assertTrue(schedule.periods.all {
            it.scheduleEntries.all { sr ->
                sr.periodId == it.id && sr.id != null && sr.startTime != null
            }
        })
        println("Periods: ")
        schedule.periods.forEach {
            val mats = schedule.mats.filter { mat -> mat.id == it.id }
            println("\n==== \n==== \n====")
            println("${it.id} -> ${it.name}")
            println("------------------ MATS: ${mats.size} --------------")
            mats.forEach { mat ->
                val mattimes = fstms.filter { fs -> fs.matId == mat.id }
                println("${mat.id} -> ${mattimes.size} -> \n${mattimes.joinToString(separator = "\n") { f -> "${f.fightId} -> ${f.startTime} -> ${f.numberOnMat}" }}")
            }
            println("------------------ SCHEDULE REQUIREMENTS ${it.scheduleRequirements?.size} -----------------")
            it.scheduleRequirements?.forEach { e ->
                println("${e.id} / start = ${e.startTime} / end = ${e.endTime} / categories: ${e.categoryIds?.distinct()?.size} / mat = ${e.matId} / fights: ${e.fightIds?.distinct()?.joinToString("\n")}")
            }
            println("------------------ SCHEDULE ENTRIES ${it.scheduleEntries?.size} -----------------")
            it.scheduleEntries.forEach { e ->
                println("${e.id} /  start =  ${e.startTime} / end =  ${e.endTime} / categories: ${e.categoryIds?.distinct()?.size}  / mats = ${getMatIds(e)} / fights: ${e.fightIds?.distinct()?.size}")
            }
        }
    }

    private fun printSchedule(tuple: Tuple2<ScheduleDTO, List<FightStartTimePairDTO>>, fights: List<Pair<StageDescriptorDTO, List<FightDescriptionDTO>>>): Pair<ScheduleDTO, List<FightStartTimePairDTO>> {
        val schedule = tuple.a
        val fstms = tuple.b

        println("Fights: ")
        fights.forEach { pair ->
            println("${pair.first.id} -> ${pair.second.filter { dto -> !ScheduleService.obsoleteFight(dto.toPojo(), dto.scores.map { it.toPojo(dto.id) }) }.size}")
        }

        println("FightStartTimes: ")
        fstms.sortedBy { it.startTime }.forEach { fstm ->
            println("${fstm.fightId} -> ${fstm.startTime} at mat ${fstm.matId}")
        }
        return Pair(schedule, fstms)
    }

    @Test
    fun testScheduleGenerationLoad() {
        val competitionId = "competitionId"
        val stageIdToCategory = listOf("stageid1" to testBracketsDataGenerationUtils.category1(),
                "stageid2" to testBracketsDataGenerationUtils.category2(),
                "stageid3" to testBracketsDataGenerationUtils.category3(),
                "stageid4" to testBracketsDataGenerationUtils.category4())
        val competitorNumbers = 500
        val fights = stageIdToCategory.map {
            testBracketsDataGenerationUtils.generateGroupFights(
                    stageId = it.first,
                    competitionId = competitionId,
                    additionalGroupSortingDescriptors = emptyArray(),
                    categoryId = it.second.id,
                    groupSizes = listOf(competitorNumbers)
            )
        }.map { stageToFights -> stageToFights.copy(second = stageToFights.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }) }
        val flatFights = fights.flatMap { it.second }

        val start = System.currentTimeMillis()
        println("Start generating.")
        val tuple = testBracketsDataGenerationUtils.generateSchedule(stageIdToCategory, fights, competitionId, competitorNumbers)
        println("Finish generating schedule. Took ${(System.currentTimeMillis() - start).toDouble() / 1000}s")
        val schedule = tuple.a
        val fstms = tuple.b

        println("Fights: ")
        fights.forEach { pair ->
            println("${pair.first} -> ${pair.second.filter { dto -> !ScheduleService.obsoleteFight(dto.toPojo(), dto.scores.map { it.toPojo(dto.id) }) }.size}")
        }

        assertNotNull(schedule)
        assertNotNull(schedule.periods)
        assertEquals(flatFights.size, fstms.size)
    }

}
