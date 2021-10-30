package compman.compsrv.query.model

case class CompetitionState(
  id: String,
  properties: CompetitionProperties,
  periods: List[Period],
  categories: List[Category],
  stages: List[StageDescriptor],
  registrationInfo: Option[RegistrationInfo]
)
