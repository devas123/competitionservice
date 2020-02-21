package compman.compsrv.service.fight

import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class FightServiceFactory(private val fightServices: List<FightsService>) {
    fun supportedBracketTypes(): List<BracketType> {
        return fightServices.flatMap { it.supportedBracketTypes() }
    }

    fun generateStageFights(competitionId: String, categoryId: String, stage: StageDescriptorDTO, compssize: Int, duration: BigDecimal, competitors: List<CompetitorDTO>, outputSize: Int): List<FightDescriptionDTO> {
        return fightServices.first { it.supportedBracketTypes().contains(stage.bracketType) }.generateStageFights(competitionId, categoryId, stage, compssize, duration, competitors, outputSize)
    }

    fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType, distributionType: DistributionType): List<FightDescriptionDTO> {
        return fightServices.first { it.supportedBracketTypes().contains(bracketType) }.distributeCompetitors(competitors, fights, bracketType, distributionType)
    }

    fun buildStageResults(bracketType: BracketType, stageStatus: StageStatus, fights: List<FightDescriptionDTO>, stageId: String, competitionId: String, pointsAssignmentDescriptors: List<FightResultOptionDTO>): List<CompetitorStageResultDTO> {
       return fightServices.first { it.supportedBracketTypes().contains(bracketType) }.buildStageResults(bracketType, stageStatus, fights, stageId, competitionId, pointsAssignmentDescriptors)
    }
}