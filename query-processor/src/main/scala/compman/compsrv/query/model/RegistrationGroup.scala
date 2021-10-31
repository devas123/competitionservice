package compman.compsrv.query.model


case class RegistrationInfo(registrationGroups: Map[String, RegistrationGroup], registrationPeriods: Map[String, RegistrationPeriod])

case class RegistrationGroup(
  competitionId: String,
  id: String,
  isDefaultGroup: Boolean,
  registrationFee: Option[RegistrationFee],
  categories: Set[String]
)

case class RegistrationFee(currency: String, amount: Int, remainder: Option[Int])
