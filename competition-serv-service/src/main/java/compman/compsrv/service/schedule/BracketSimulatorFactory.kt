package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.CompScore
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.brackets.BracketType
import org.springframework.stereotype.Component

@Component
class BracketSimulatorFactory {
    fun createSimulator(stageId: String, categoryId: String, fights: List<FightDescription>, bracketType: BracketType,
                        competitorsNumber: Int, getFightScores: (id: String) -> List<CompScore>): IBracketSimulator =
            when (bracketType) {
                BracketType.SINGLE_ELIMINATION, BracketType.CHAVE_DE_3 -> SingleEliminationSimulator(stageId, getFightScores, categoryId, fights,
                        competitorsNumber == 3)
                BracketType.DOUBLE_ELIMINATION -> DoubleEliminationSimulator(stageId, getFightScores, categoryId, fights)
                BracketType.GROUP -> GroupSimulator(stageId, categoryId, fights)
                else -> TODO()
            }
}