package compman.compsrv.logic

import compman.compsrv.Utils
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.schedule.ScheduleDTO
import monocle.macros.GenLens

final case class CompetitionState(
  id: String,
  competitors: Option[Map[String, CompetitorDTO]],
  competitionProperties: Option[CompetitionPropertiesDTO],
  stages: Option[Map[String, StageDescriptorDTO]],
  fights: Option[Map[String, FightDescriptionDTO]],
  categories: Option[Map[String, CategoryDescriptorDTO]],
  registrationInfo: Option[RegistrationInfoDTO],
  schedule: Option[ScheduleDTO],
  revision: Long)

object CompetitionState {
  private val fightsLens = GenLens[CompetitionState](_.fights)
  private val stagesLens = GenLens[CompetitionState](_.stages)

  final implicit class CompetitionStateOps(private val c: CompetitionState) extends AnyVal {
    def updateFights(fights: Seq[FightDescriptionDTO]): CompetitionState = fightsLens.modify(f => f.map(f => f ++ Utils.groupById(fights)(_.getId)))(c)

    def updateStage(stage: StageDescriptorDTO): CompetitionState =
      stagesLens.modify(s => s.map(stgs => stgs + (stage.getId -> stage)))(c)
  }
}
