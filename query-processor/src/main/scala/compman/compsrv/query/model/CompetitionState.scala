package compman.compsrv.query.model

import compman.compsrv.query.model.CompetitionState.CompetitionInfoTemplate


case class CompetitionState(
  id: Option[String],
  eventsTopic: Option[String],
  properties: Option[CompetitionProperties],
  periods: Option[Map[String, Period]],
  categories: Option[Map[String, Category]],
  stages: Option[Map[String, StageDescriptor]],
  registrationInfo: Option[RegistrationInfo],
  infoTemplate: Option[CompetitionInfoTemplate],
                           )

object CompetitionState {
  case class CompetitionInfoTemplate(template: Array[Byte])
}
