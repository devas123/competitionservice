package compman.compsrv.service

import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.schedule.ScheduleEntryDTO
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
    fun testScheduleGeneration() {
        val competitionId = "competitionId"
        val categories = listOf("stageid1" to testBracketsDataGenerationUtils.category1(fightDuration),
                "stageid2" to testBracketsDataGenerationUtils.category2(fightDuration),
                "stageid3" to testBracketsDataGenerationUtils.category3(fightDuration),
                "stageid4" to testBracketsDataGenerationUtils.category4(fightDuration))
        val competitorNumbers = 10
        val fights = categories.map {
            val competitors = FightsService.generateRandomCompetitorsForCategory(competitorNumbers, competitorNumbers, it.second.id, competitionId)
            val stage = testBracketsDataGenerationUtils.createSingleEliminationStage(competitionId, it.second.id, it.first, competitorNumbers)
            stage to testBracketsDataGenerationUtils.generateFilledFights(competitionId, it.second, stage, competitors, BigDecimal.valueOf(fightDuration))
        }.map { dto -> dto.copy(second = dto.second.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }) }
        val flatFights = fights.flatMap { it.second }.filter { f -> !ScheduleService.obsoleteFight(f.toPojo(), f.scores.map { it.toPojo(f.id) }) }

        val tuple = testBracketsDataGenerationUtils.generateSchedule(categories, fights, competitionId, competitorNumbers)
        val schedule = tuple.a
        val fstms = tuple.b

        println("Fights: ")
        fights.forEach { pair ->
            println("${pair.first} -> ${pair.second.filter { dto -> !ScheduleService.obsoleteFight(dto.toPojo(), dto.scores.map { it.toPojo(dto.id) }) }.size}")
        }

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

    @Test
    fun testScheduleGenerationLoad() {
        val competitionId = "competitionId"
        val stageIdToCategory = listOf("stageid1" to testBracketsDataGenerationUtils.category1(fightDuration),
                "stageid2" to testBracketsDataGenerationUtils.category2(fightDuration),
                "stageid3" to testBracketsDataGenerationUtils.category3(fightDuration),
                "stageid4" to testBracketsDataGenerationUtils.category4(fightDuration))
        val competitorNumbers = 110
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
        assertEquals(flatFights.size, schedule.periods.map { it.scheduleEntries.map { entryDTO -> entryDTO.numberOfFights }.sum() }.sum())
        assertTrue(flatFights.fold(true) { acc, f -> acc && fstms.any { it.fightId == f.id } })
        assertEquals(stageIdToCategory.size, schedule.periods.flatMap { it.scheduleEntries.flatMap { scheduleEntryDTO -> scheduleEntryDTO.categoryIds.toList() } }.distinct().size)
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

}
