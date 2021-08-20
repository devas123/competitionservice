package compman.compsrv.logic.service.generate

import compman.compsrv.model.dto.brackets.{BracketType, StageDescriptorDTO}
import compman.compsrv.model.dto.competition.{CompetitorDTO, FightDescriptionDTO}

trait FightsGenerateService {
  def generateStageFights(
      competitionId: String,
      categoryId: String,
      stage: StageDescriptorDTO,
      compssize: Int,
      duration: BigDecimal,
      competitors: List[CompetitorDTO],
      outputSize: Int
  ): PartialFunction[BracketType, List[FightDescriptionDTO]]
}

object FightsGenerateService {
  val singleEliminationGenerator: FightsGenerateService =
    (
        competitionId: String,
        categoryId: String,
        stage: StageDescriptorDTO,
        compssize: Int,
        duration: BigDecimal,
        competitors: List[CompetitorDTO],
        outputSize: Int
    ) => { case BracketType.SINGLE_ELIMINATION =>
      List.empty
    }
}
