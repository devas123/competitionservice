package compman.compsrv.query.model

case class RegistrationInfo(
  id: String,
  registrationGroups: Map[String, RegistrationGroup],
  registrationPeriods: Map[String, RegistrationPeriod],
  registrationOpen: Boolean
)

object RegistrationInfo {
  def apply(id: String): RegistrationInfo = apply(id, Map.empty, Map.empty, registrationOpen = false)
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
