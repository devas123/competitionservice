package compman.compsrv.model

import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._

trait CompetitionState {
  def id: String
  def competitors: Option[Seq[CompetitorDTO]]
  def competitionProperties: Option[CompetitionPropertiesDTO]
  def stages: Option[StageDescriptorDTO]
  def fights: Option[Seq[FightDescriptionDTO]]
  def categories: Option[Seq[CategoryDescriptorDTO]]
  def registrationInfo: Option[RegistrationInfoDTO]
  def revision: Long

  def createCopy(
      competitors: Option[Seq[CompetitorDTO]],
      competitionProperties: Option[CompetitionPropertiesDTO],
      stages: Option[StageDescriptorDTO],
      fights: Option[Seq[FightDescriptionDTO]],
      categories: Option[Seq[CategoryDescriptorDTO]],
      registrationInfo: Option[RegistrationInfoDTO],
      revision: Long
  ): CompetitionState
}

case class CompetitionStateImpl(
    id: String,
    competitors: Option[Seq[CompetitorDTO]],
    competitionProperties: Option[CompetitionPropertiesDTO],
    stages: Option[StageDescriptorDTO],
    fights: Option[Seq[FightDescriptionDTO]],
    categories: Option[Seq[CategoryDescriptorDTO]],
    registrationInfo: Option[RegistrationInfoDTO],
    revision: Long
) extends CompetitionState {
  override def createCopy(
      competitors: Option[Seq[CompetitorDTO]],
      competitionProperties: Option[CompetitionPropertiesDTO],
      stages: Option[StageDescriptorDTO],
      fights: Option[Seq[FightDescriptionDTO]],
      categories: Option[Seq[CategoryDescriptorDTO]],
      registrationInfo: Option[RegistrationInfoDTO],
      revision: Long
  ): CompetitionState = this.copy(
    id,
    competitors,
    competitionProperties,
    stages,
    fights,
    categories,
    registrationInfo,
    revision
  )
}
