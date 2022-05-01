package compman.compsrv.query.model


case class RegistrationInfo(registrationGroups: Map[String, RegistrationGroup], registrationPeriods: Map[String, RegistrationPeriod])

object RegistrationInfo {
  def apply(): RegistrationInfo = apply(Map.empty, Map.empty)
}

case class RegistrationGroup(
  competitionId: String,
  id: String,
  displayName: Option[String],
  isDefaultGroup: Boolean,
  registrationFee: Option[RegistrationFee],
  categories: Set[String]
)

case class RegistrationFee(currency: String, amount: Int, remainder: Option[Int])
