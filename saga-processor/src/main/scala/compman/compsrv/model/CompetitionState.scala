package compman.compsrv.model

import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.schedule.ScheduleDTO

trait CompetitionState {
  def id: String
  def competitors: Option[Seq[CompetitorDTO]]
  def competitionProperties: Option[CompetitionPropertiesDTO]
  def stages: Option[Seq[StageDescriptorDTO]]
  def fights: Option[Seq[FightDescriptionDTO]]
  def categories: Option[Seq[CategoryDescriptorDTO]]
  def registrationInfo: Option[RegistrationInfoDTO]
  def schedule: Option[ScheduleDTO]
  def revision: Long

  def createCopy(
      competitors: Option[Seq[CompetitorDTO]],
      competitionProperties: Option[CompetitionPropertiesDTO],
      stages: Option[Seq[StageDescriptorDTO]],
      fights: Option[Seq[FightDescriptionDTO]],
      categories: Option[Seq[CategoryDescriptorDTO]],
      registrationInfo: Option[RegistrationInfoDTO],
      schedule: Option[ScheduleDTO],
      revision: Long
  ): CompetitionState
}

case class CompetitionStateImpl(
    id: String,
    competitors: Option[Seq[CompetitorDTO]],
    competitionProperties: Option[CompetitionPropertiesDTO],
    stages: Option[Seq[StageDescriptorDTO]],
    fights: Option[Seq[FightDescriptionDTO]],
    categories: Option[Seq[CategoryDescriptorDTO]],
    registrationInfo: Option[RegistrationInfoDTO],
    schedule: Option[ScheduleDTO],
    revision: Long
) extends CompetitionState {
  override def createCopy(
      competitors: Option[Seq[CompetitorDTO]],
      competitionProperties: Option[CompetitionPropertiesDTO],
      stages: Option[Seq[StageDescriptorDTO]],
      fights: Option[Seq[FightDescriptionDTO]],
      categories: Option[Seq[CategoryDescriptorDTO]],
      registrationInfo: Option[RegistrationInfoDTO],
      schedule: Option[ScheduleDTO],
      revision: Long
  ): CompetitionState = this.copy(
    id,
    competitors,
    competitionProperties,
    stages,
    fights,
    categories,
    registrationInfo,
    schedule,
    revision
  )
}
