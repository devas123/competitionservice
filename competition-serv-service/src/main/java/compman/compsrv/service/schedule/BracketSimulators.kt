package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.CompScore
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.brackets.StageRoundType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface IBracketSimulator {
    fun isEmpty(): Boolean
    fun getNextRound(): List<FightDescription>
    val stageIds: Set<String>
    val categoryId: String
}

class SingleEliminationSimulator(val stageId: String, val getFightScores: (id: String) -> List<CompScore>, override val categoryId: String, fights: List<FightDescription>, threeCompetitorCategory: Boolean) : IBracketSimulator {
    private val fightsByRounds: MutableList<List<FightDescription>>
    override val stageIds = setOf(stageId)

    init {
        fightsByRounds = if (fights.isNotEmpty()) {
            fights
                    .asSequence()
                    .filter { it.round != null && !ScheduleService.obsoleteFight(it, getFightScores(it.id), threeCompetitorCategory) }
                    .groupBy { it.round ?: 0 }
                    .toList()
                    .sortedBy { it.first }
                    .fold(emptyList<List<FightDescription>>()) { acc, pair -> acc + listOf(pair.second) }
                    .toMutableList()
        } else {
            ArrayList()
        }
    }

    override fun isEmpty() = this.fightsByRounds.isEmpty()

    override fun getNextRound(): List<FightDescription> {
        return if (this.fightsByRounds.size > 0) {
            this.fightsByRounds.removeAt(0)
        } else {
            ArrayList()
        }
    }
}

class DoubleEliminationSimulator(val stageId: String, val getFightScores: (id: String) -> List<CompScore>, override val categoryId: String, fights: List<FightDescription>) : IBracketSimulator {
    private var fightsByBracketTypeAndRounds: List<List<FightDescription>>
    override val stageIds = setOf(stageId)


    companion object {
        private val log: Logger = LoggerFactory.getLogger(DoubleEliminationSimulator::class.java)
    }

    init {
        fightsByBracketTypeAndRounds = if (fights.isNotEmpty()) {
            fights
                    .asSequence()
                    .filter { it.round != null && !ScheduleService.obsoleteFight(it, getFightScores(it.id)) }
                    .groupBy { it.round ?: 0 }
                    .toList()
                    .sortedBy { it.first }
                    .fold(emptyList()) { acc, pair ->
                        val byRoundType = pair.second.groupBy { it.roundType!! }.toList()
                        acc + listOf(byRoundType.filter { it.first == StageRoundType.WINNER_BRACKETS.name }.flatMap { it.second }) +
                                listOf(byRoundType.filter { it.first == StageRoundType.LOSER_BRACKETS.name }.flatMap { it.second }) +
                                listOf(byRoundType.filter { it.first == StageRoundType.THIRD_PLACE_FIGHT.name }.flatMap { it.second }) +
                                listOf(byRoundType.filter { it.first == StageRoundType.GRAND_FINAL.name }.flatMap { it.second })
                    }
        } else {
            emptyList()
        }
    }

    override fun isEmpty(): Boolean {
        log.info("${this.fightsByBracketTypeAndRounds.size}")
        return this.fightsByBracketTypeAndRounds.isEmpty()
    }

    override fun getNextRound(): List<FightDescription> {
        val result = this.fightsByBracketTypeAndRounds[0]
        this.fightsByBracketTypeAndRounds = this.fightsByBracketTypeAndRounds.drop(1)
        return result
    }
}

class GroupSimulator(val stageId: String, override val categoryId: String, fights: List<FightDescription>) : IBracketSimulator {
    private val fightsByRounds: MutableList<List<FightDescription>>
    override val stageIds = setOf(stageId)


    init {
        fightsByRounds = if (fights.isNotEmpty()) {
            fights
                    .asSequence()
                    .filter { it.round != null }
                    .groupBy { it.round ?: 0 }
                    .toList()
                    .sortedBy { it.first }
                    .fold(emptyList<List<FightDescription>>()) { acc, pair -> acc + listOf(pair.second) }
                    .toMutableList()
        } else {
            ArrayList()
        }
    }

    override fun isEmpty() = this.fightsByRounds.isEmpty()

    override fun getNextRound(): List<FightDescription> {
        return if (this.fightsByRounds.size > 0) {
            this.fightsByRounds.removeAt(0)
        } else {
            ArrayList()
        }
    }
}
