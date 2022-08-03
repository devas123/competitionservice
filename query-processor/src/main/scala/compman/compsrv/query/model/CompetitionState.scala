package compman.compsrv.query.model

case class CompetitionState(
  id: String,
  eventsTopic: String,
  properties: CompetitionProperties,
  periods: Map[String, Period],
  categories: Map[String, Category],
  stages: Map[String, StageDescriptor],
  registrationInfo: RegistrationInfo
)
