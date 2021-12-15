package compman.compsrv.logic

import compman.compsrv.Utils
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.schedule.ScheduleDTO
import monocle.macros.GenLens
import monocle.Lens

final case class CompetitionState(
  id: String,
  competitors: Option[Map[String, CompetitorDTO]],
  competitionProperties: Option[CompetitionPropertiesDTO],
  stages: Option[Map[String, StageDescriptorDTO]],
  fights: Option[Map[String, FightDescriptionDTO]],
  categories: Option[Map[String, CategoryDescriptorDTO]],
  registrationInfo: Option[RegistrationInfoDTO],
  schedule: Option[ScheduleDTO],
  revision: Long
)

object CompetitionState {
  private val fightsLens         = GenLens[CompetitionState](_.fights)
  private val stagesLens         = GenLens[CompetitionState](_.stages)
  private val compPropertiesLens = GenLens[CompetitionState](_.competitionProperties)
  private val categoriesLens     = GenLens[CompetitionState](_.categories)
  private val competitorsLens    = GenLens[CompetitionState](_.competitors)

  final implicit class CompetitionStateOps(private val c: CompetitionState) extends AnyVal {
    def updateStatus(competitionStatus: CompetitionStatus): CompetitionState = compPropertiesLens
      .modify(p => p.map(_.setStatus(competitionStatus)))(c)
    def deleteCategory(id: String): CompetitionState = categoriesLens.modify(p => p.map(_ - id))(c)
    def updateFights(fights: Seq[FightDescriptionDTO]): CompetitionState = fightsLens
      .modify(f => f.map(f => f ++ Utils.groupById(fights)(_.getId)))(c)
    def fightsApply(
      update: Option[Map[String, FightDescriptionDTO]] => Option[Map[String, FightDescriptionDTO]]
    ): CompetitionState = lensApplyOptionsl(fightsLens)(update)
    def competitorsApply(
      update: Option[Map[String, CompetitorDTO]] => Option[Map[String, CompetitorDTO]]
    ): CompetitionState = lensApplyOptionsl(competitorsLens)(update)

    private def lensApplyOptionsl[T](lens: Lens[CompetitionState, Option[Map[String, T]]])(
      update: Option[Map[String, T]] => Option[Map[String, T]]
    ) = lens.modify(update)(c)

    def updateStage(stage: StageDescriptorDTO): CompetitionState = stagesLens
      .modify(s => s.map(stgs => stgs + (stage.getId -> stage)))(c)
  }
}
