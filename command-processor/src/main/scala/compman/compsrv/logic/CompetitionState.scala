package compman.compsrv.logic

import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.schedule.ScheduleDTO

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
  final implicit class CompetitionStateOps(private val c: CompetitionState) extends AnyVal {
    def updateFights(fights: Seq[FightDescriptionDTO]): CompetitionState = c
      .copy(fights = c.fights.map(f => f ++ fights.groupMapReduce(_.getId)(identity)((a, _) => a)))

    def updateStage(stage: StageDescriptorDTO): CompetitionState = c
      .copy(stages = c.stages.map(stgs => stgs + (stage.getId -> stage)))
  }
}
