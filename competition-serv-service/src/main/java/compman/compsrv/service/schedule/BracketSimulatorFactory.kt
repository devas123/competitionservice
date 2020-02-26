package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.brackets.BracketType
import org.springframework.stereotype.Component

@Component
class BracketSimulatorFactory {
    fun createSimulator(stageId: String, categoryId: String, fights: List<FightDescription>, bracketType: BracketType,
                        competitorsNumber: Int): IBracketSimulator =
            when (bracketType) {
                BracketType.SINGLE_ELIMINATION, BracketType.CHAVE_DE_3 -> SingleEliminationSimulator(stageId, categoryId, fights,
                        competitorsNumber == 3)
                BracketType.DOUBLE_ELIMINATION -> DoubleEliminationSimulator(stageId, categoryId, fights)
                BracketType.GROUP, BracketType.MULTIPLE_GROUPS -> GroupSimulator(stageId, categoryId, fights)
                else -> TODO()
            }
}