package compman.compsrv.service.schedule

import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.springframework.stereotype.Component

@Component
class BracketSimulatorFactory {
    fun createSimulator(stageId: String, categoryId: String, fights: List<FightDescriptionDTO>, bracketType: BracketType,
                        competitorsNumber: Int): IBracketSimulator =
            when (bracketType) {
                BracketType.SINGLE_ELIMINATION, BracketType.CHAVE_DE_3 -> SingleEliminationSimulator(stageId, categoryId, fights,
                        competitorsNumber == 3)
                BracketType.DOUBLE_ELIMINATION -> DoubleEliminationSimulator(stageId, categoryId, fights)
                else -> TODO()
            }
}