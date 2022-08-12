package compman.compsrv.query.model

import compman.compsrv.query.model.CompetitionState.CompetitionInfo


case class CompetitionState(
                             id: Option[String],
                             eventsTopic: Option[String],
                             properties: Option[CompetitionProperties],
                             periods: Option[Map[String, Period]],
                             categories: Option[Map[String, Category]],
                             stages: Option[Map[String, StageDescriptor]],
                             registrationInfo: Option[RegistrationInfo],
                             info: Option[CompetitionInfo],
                           )

object CompetitionState {
  case class CompetitionInfo(template: Option[Array[Byte]], image: Option[Array[Byte]])
}
