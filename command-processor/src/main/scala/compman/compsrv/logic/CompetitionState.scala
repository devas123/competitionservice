package compman.compsrv.logic

import compman.compsrv.Utils
import compservice.model.protobuf.model._

object CompetitionState {
  final implicit class CompetitionStateOps(private val c: CommandProcessorCompetitionState) extends AnyVal {
    def updateStatus(competitionStatus: CompetitionStatus): CommandProcessorCompetitionState = c
      .update(_.competitionProperties.setIfDefined(c.competitionProperties.map(_.withStatus(competitionStatus))))
    def deleteCategory(id: String): CommandProcessorCompetitionState = c.update(_.categories := c.categories - id)
    def updateFights(fights: Seq[FightDescription]): CommandProcessorCompetitionState = c
      .update(_.fights := c.fights ++ Utils.groupById(fights)(_.id))
    def updateStage(stage: StageDescriptor): CommandProcessorCompetitionState = c
      .update(_.stages := c.stages + (stage.id -> stage))
    def fightsApply(
      update: Option[Map[String, FightDescription]] => Option[Map[String, FightDescription]]
    ): CommandProcessorCompetitionState = c.update(_.fights.setIfDefined(update(Some(c.fights))))
    def competitorsApply(
      update: Option[Map[String, Competitor]] => Option[Map[String, Competitor]]
    ): CommandProcessorCompetitionState = c.update(_.competitors.setIfDefined(update(Some(c.competitors))))
  }
}
