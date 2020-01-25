package compman.compsrv.service.schedule

import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.brackets.BracketType
import org.springframework.stereotype.Component

@Component
class BracketSimulatorFactory {
    fun createSimulator(stageId: String, categoryId: String, fights: List<FightDescription>, bracketType: BracketType): IBracketSimulator =
            when (bracketType) {
                BracketType.SINGLE_ELIMINATION, BracketType.CHAVE_DE_3 -> SingleEliminationSimulator(stageId, categoryId, fights,
                        fights.flatMap { it.scores ?: mutableListOf() }.map { it.competitor }.distinctBy { it.id!! }.size == 3)
                BracketType.DOUBLE_ELIMINATION -> DoubleEliminationSimulator(stageId, categoryId, fights)
                else -> TODO()
            }
}