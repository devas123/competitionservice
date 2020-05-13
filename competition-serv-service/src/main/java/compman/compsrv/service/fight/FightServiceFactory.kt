package compman.compsrv.service.fight

import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class FightServiceFactory(private val fightServices: List<FightsService>) {

    private fun findService(bracketType: BracketType) = fightServices.first { it.supportedBracketTypes().contains(bracketType) }



    fun generateStageFights(competitionId: String, categoryId: String, stage: StageDescriptorDTO, compssize: Int, duration: BigDecimal, competitors: List<CompetitorDTO>, outputSize: Int): List<FightDescriptionDTO> {
        return findService(stage.bracketType).generateStageFights(competitionId, categoryId, stage, compssize, duration, competitors, outputSize)
    }

    fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType): List<FightDescriptionDTO> {
        return findService(bracketType).distributeCompetitors(competitors, fights, bracketType)
    }

    fun buildStageResults(bracketType: BracketType, stageStatus: StageStatus, stageType: StageType, fights: List<FightDescriptionDTO>, stageId: String, competitionId: String, pointsAssignmentDescriptors: List<FightResultOptionDTO>): List<CompetitorStageResultDTO> {
        return findService(bracketType).buildStageResults(bracketType, stageStatus, stageType, fights, stageId, competitionId, pointsAssignmentDescriptors)
    }

    fun applyStageInputDescriptorToResultsAndFights(bracketType: BracketType, inputDescriptor: StageInputDescriptorDTO, previousStageId: String, fightResultOptions: (stageId: String) -> List<FightResultOptionDTO>,
                                                    stageResults: (stageId: String) -> List<CompetitorStageResultDTO>, fights: (stageId: String) -> List<FightDescriptionDTO>): List<String> {
        return findService(bracketType).applyStageInputDescriptorToResultsAndFights(inputDescriptor, previousStageId, fightResultOptions, stageResults, fights)
    }
}