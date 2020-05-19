package compman.compsrv.service

import arrow.core.Tuple4
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.copy
import org.slf4j.LoggerFactory
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
        private val log = LoggerFactory.getLogger(AbstractGenerateServiceTest::class.java)
        val fightResultOptions = FightResultOptionDTO.values.map { it.setId(IDGenerator.uid()) }
        fun generateFightResult(fight: FightDescriptionDTO): Pair<FightDescriptionDTO, String?> {
            val scores = fight.scores?.filter { !it.competitorId.isNullOrBlank() }?.toList()
            val competitor = fight.fightResult?.winnerId ?: when (scores?.size) {
                2 -> {
                    scores[Random.nextInt(2)].competitorId
                }
                1 -> {
                    scores.first().competitorId
                }
                else -> {
                    null
                }
            }
            return fight.copy(fightResult = fight.fightResult
                    ?: competitor?.let { FightResultDTO(it, fightResultOptions[Random.nextInt(3)].id, "bla bla bla") }) to competitor
        }

        private fun checkFightConnectionLaws(fights: List<FightDescriptionDTO>) {
            fights.filter { it.roundType == StageRoundType.WINNER_BRACKETS && it.round == 0 }.forEach { f ->
                assertTrue("Fight ${f.id} has parent references but it shouldn't") { f.scores?.any { s -> !s.parentFightId.isNullOrBlank() } != true }
            }
            fights.filter { it.roundType == StageRoundType.WINNER_BRACKETS && it.round > 0 }.forEach { f ->
                assertTrue("Fight ${f.id} from winner brackets and round ${f.round} has no parent references but it should") { f.scores?.all { s -> !s.parentFightId.isNullOrBlank() } == true }
            }
            fights.filter { it.roundType == StageRoundType.LOSER_BRACKETS }.forEach { f ->
                assertTrue("Fight ${f.id} from loser brackets and round ${f.round} has no parent references but it should") { f.scores?.all { s -> !s.parentFightId.isNullOrBlank() } == true }
            }
            val allFightsHaveValidConnections = fights.fold(emptyList<Tuple4<String, String, FightReferenceType, Int>>()) { acc, f ->
                var res = acc
                if (f.winFight
                                ?.let { wf ->
                                    fights.count { fc ->
                                        fc.id == wf &&
                                                fc.scores?.any { s -> s.parentFightId == f.id && s.parentReferenceType == FightReferenceType.WINNER } == true
                                    } == 1
                                } == false) {
                    res = res + Tuple4(f.id, f.winFight, FightReferenceType.WINNER, fights.count { fc ->
                        fc.id == f.winFight &&
                                fc.scores?.any { s -> s.parentFightId == f.id && s.parentReferenceType == FightReferenceType.WINNER } == true
                    })
                }
                if (f.loseFight
                                ?.let { wf ->
                                    fights.count { fc ->
                                        fc.id == wf &&
                                                fc.scores?.any { s -> s.parentFightId == f.id && s.parentReferenceType == FightReferenceType.LOSER } == true
                                    } == 1
                                } == false) {
                    res = res + Tuple4(f.id, f.loseFight, FightReferenceType.LOSER, fights.count { fc ->
                        fc.id == f.loseFight &&
                                fc.scores?.any { s -> s.parentFightId == f.id && s.parentReferenceType == FightReferenceType.LOSER } == true
                    })
                }
                res
            }
            assertEquals(0, allFightsHaveValidConnections.size,
                    "Fights do not have valid connections. Invalid fights are: ${allFightsHaveValidConnections.joinToString("\n") {
                        "id: ${it.a} / ref: ${it.b} / type: ${it.c} / numberOfConn: ${it.d}"
                    }}")
        }

        fun checkWinnerFightsLaws(fights: List<FightDescriptionDTO>, firstRoundSize: Int) {
            assertTrue(DoubleMath.isPowerOfTwo(firstRoundSize.toDouble()))
            checkFightConnectionLaws(fights)
            val lastRound = DoubleMath.log2(firstRoundSize.toDouble()).toInt() + 1
            val totalSize = (0 until lastRound).fold(0) { acc, n -> acc + 2.toDouble().pow(n.toDouble()).toInt() }
            assertEquals(totalSize, fights.size)
            (lastRound - 1 downTo 0).forEachIndexed { i, n -> assertEquals(2.toDouble().pow(n.toDouble()).toInt(), fights.filter { it.round == i }.size) }
            assertTrue { fights.filter { it.round == 0 }.none { f -> f.scores?.any { it.parentFightId != null } == true } }
            assertTrue { fights.filter { it.round != 0 }.none { f -> f.scores?.any { it.parentFightId == null } == true } }
            assertTrue { fights.filter { it.round == lastRound - 1 }.none { it.winFight != null } }
            assertTrue { fights.filter { it.round != lastRound - 1 }.none { it.winFight == null } }
        }

        fun checkDoubleEliminationLaws(doubleEliminationBracketFights: List<FightDescriptionDTO>, firstWinnerRoundSize: Int) {
            assertTrue(DoubleMath.isPowerOfTwo(firstWinnerRoundSize.toDouble()))
            assertTrue(firstWinnerRoundSize > 1)
            checkFightConnectionLaws(doubleEliminationBracketFights)
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