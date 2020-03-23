package compman.compsrv.service

import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.util.copy
import org.testcontainers.shaded.com.google.common.math.DoubleMath
import java.math.BigDecimal
import java.util.*
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

open class AbstractGenerateServiceTest {
    companion object {
        val fightResultOptions = FightResultOptionDTO.values.map { it.setId(UUID.randomUUID().toString()) }
        fun generateFightResult(fight: FightDescriptionDTO): Pair<FightDescriptionDTO, CompetitorDTO?> {
            val scores = fight.scores?.toList()
            val competitor = when (scores?.size) {
                2 -> {
                    scores[Random.nextInt(2)].competitor
                }
                1 -> {
                    scores.first().competitor
                }
                else -> {
                    null
                }
            }
            return fight.copy(fightResult = competitor?.let { FightResultDTO(it.id, fightResultOptions[Random.nextInt(3)].id, "bla bla bla") }) to competitor
        }

        fun checkWinnerFightsLaws(fights: List<FightDescriptionDTO>, firstRoundSize: Int) {
            assertTrue(DoubleMath.isPowerOfTwo(firstRoundSize.toDouble()))
            val lastRound = DoubleMath.log2(firstRoundSize.toDouble()).toInt() + 1
            val totalSize = (0 until lastRound).fold(0) { acc, n -> acc + 2.toDouble().pow(n.toDouble()).toInt() }
            assertEquals(totalSize, fights.size)
            (lastRound - 1 downTo 0).forEachIndexed { i, n -> assertEquals(2.toDouble().pow(n.toDouble()).toInt(), fights.filter { it.round == i }.size) }
            assertTrue { fights.filter { it.round == 0 }.none { it.parentId1 != null || it.parentId2 != null } }
            assertTrue { fights.filter { it.round != 0 }.none { it.parentId1 == null || it.parentId2 == null } }
            assertTrue { fights.filter { it.round == lastRound - 1 }.none { it.winFight != null } }
            assertTrue { fights.filter { it.round != lastRound - 1 }.none { it.winFight == null } }
        }

        fun checkDoubleEliminationLaws(doubleEliminationBracketFights: List<FightDescriptionDTO>, firstWinnerRoundSize: Int) {
            assertTrue(DoubleMath.isPowerOfTwo(firstWinnerRoundSize.toDouble()))
            assertTrue(firstWinnerRoundSize > 1)
            doubleEliminationBracketFights.forEach {
                assertNotNull(it.roundType)
                assertNotNull(it.round)
                assertNotNull(it.duration)
                assertNotNull(it.fightName)
            }
            val lastWinnerRound = DoubleMath.log2(firstWinnerRoundSize.toDouble()).toInt() + 1
            val totalWinnerFightsSizes = (0 until lastWinnerRound).fold(listOf(1)) { acc, n -> acc + 2.toDouble().pow(n.toDouble()).toInt() }
            val lastLoserRound = (lastWinnerRound - 2) * 2 + 1
            val totalLoserFightsSizes = (0..lastLoserRound).fold(emptyList<Int>()) { acc, n ->
                val res = if (n % 2 == 0) {
                    val nextSize = if (acc.size <= 1) {
                        1
                    } else {
                        max(1, (acc.dropLast(1).lastOrNull() ?: 0) * 2)
                    }
                    acc + nextSize
                } else {
                    acc + acc.last()
                }
                res
            }
            assertEquals(totalWinnerFightsSizes.sum() + totalLoserFightsSizes.sum(), doubleEliminationBracketFights.size)
            val loserBracketsFights = doubleEliminationBracketFights.filter { it.roundType == StageRoundType.LOSER_BRACKETS }
            assertEquals(totalLoserFightsSizes.sum(), loserBracketsFights.size)
            totalLoserFightsSizes.reversed().forEachIndexed { i, n -> assertEquals(n, loserBracketsFights.filter { it.round == i }.size) }
            assertEquals(1, doubleEliminationBracketFights.filter { it.roundType == StageRoundType.GRAND_FINAL }.size)
        }
    }

    protected val duration: BigDecimal = BigDecimal.valueOf(8)
    protected val competitionId = "UG9wZW5nYWdlbiBPcGVu"
    protected val categoryId = "UG9wZW5nYWdlbiBPcGVu-UG9wZW5nYWdlbiBPcGVu"
    protected val stageId = "asoifjqwoijqwoijqpwtoj2j12-j1fpasoj"
    val category = CategoryGeneratorService.createCategory(8, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
}