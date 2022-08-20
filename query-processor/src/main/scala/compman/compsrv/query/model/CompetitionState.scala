package compman.compsrv.query.model


case class CompetitionState(
                             id: Option[String],
                             eventsTopic: Option[String],
                             properties: Option[CompetitionProperties],
                             periods: Option[Map[String, Period]],
                             categories: Option[Map[String, Category]],
                             stages: Option[Map[String, StageDescriptor]],
                             registrationInfo: Option[RegistrationInfo],
                           )