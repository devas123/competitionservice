package compman.compsrv.service

import compman.compsrv.mapping.toPojo
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduleServiceTest {
    private val fightsGenerateService = BracketsGenerateService()
    private val fightDuration = 8L


    private val testDataGenerationUtils = TestDataGenerationUtils(fightsGenerateService)

    @Test
    fun testScheduleGeneration() {
        val competitionId = "competitionId"
        val categories = listOf("stageid1" to testDataGenerationUtils.category1(fightDuration),
                "stageid2" to testDataGenerationUtils.category2(fightDuration),
                "stageid3" to testDataGenerationUtils.category3(fightDuration),
                "stageid4" to testDataGenerationUtils.category4(fightDuration))
        val competitorNumbers = 10
        val fights = categories.map {
            val competitors = FightsService.generateRandomCompetitorsForCategory(competitorNumbers, competitorNumbers, it.second, competitionId)
            it.first to testDataGenerationUtils.generateFilledFights(competitionId, it.second, it.first, competitors, BigDecimal.valueOf(fightDuration))
        }.map { dto -> dto.copy(second = dto.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo()) }) }
        val flatFights = fights.flatMap { it.second }.filter { f -> !ScheduleService.obsoleteFight(f.toPojo()) }

        val schedule = testDataGenerationUtils.generateSchedule(categories, fights, competitionId, competitorNumbers)

        println("Fights: ")
        fights.forEach {
            println("${it.first} -> ${it.second.filter { dto -> !ScheduleService.obsoleteFight(dto.toPojo()) }.size}")
        }

        assertNotNull(schedule)
        assertNotNull(schedule.periods)
        assertEquals(flatFights.size, schedule.periods.flatMap { it.mats.flatMap { descriptionDTO -> descriptionDTO.fightStartTimes.toList() } }.size)
        assertEquals(flatFights.size, schedule.periods.flatMap { it.scheduleEntries.flatMap { entryDTO -> entryDTO.fightIds.toList() } }.distinct().size)
        val fightStartTimes = schedule.periods.flatMap { it.mats.flatMap { dto -> dto.fightStartTimes.toList() } }
        val fightIdsInSchedule = schedule.periods.flatMap { it.scheduleEntries.flatMap { dto -> dto.fightIds.toList() } }
        assertTrue(flatFights.fold(true) { acc, f -> acc && fightStartTimes.any { it.fightId == f.id } })
        assertTrue(flatFights.fold(true) { acc, f -> acc && fightIdsInSchedule.contains(f.id) })
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
            println("\n==== \n==== \n====")
            println("${it.id} -> ${it.name}")
            println("------------------ MATS: ${it.mats?.size} --------------")
            it.mats.forEach { mat ->
                println("${mat.id} -> ${mat.fightStartTimes?.size} -> \n${mat.fightStartTimes?.joinToString(separator = "\n") { f -> "${f.fightId} -> ${f.startTime} -> ${f.numberOnMat}" }}")
            }
            println("------------------ SCHEDULE REQUIREMENTS ${it.scheduleRequirements?.size} -----------------")
            it.scheduleRequirements?.forEach { e ->
                println("${e.id} / start = ${e.startTime} / end = ${e.endTime} / categories: ${e.categoryIds?.distinct()?.size} / mat = ${e.matId} / fights: ${e.fightIds?.distinct()?.joinToString("\n")}")
            }
            println("------------------ SCHEDULE ENTRIES ${it.scheduleEntries?.size} -----------------")
            it.scheduleEntries.forEach { e ->
                println("${e.id} /  start =  ${e.startTime} / end =  ${e.endTime} / categories: ${e.categoryIds?.distinct()?.size}  / mat = ${e.matId} / fights: ${e.fightIds?.distinct()?.size}")
            }
        }
    }

}
