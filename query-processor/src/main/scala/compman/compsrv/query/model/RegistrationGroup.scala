package compman.compsrv.query.model

import io.getquill.Udt

case class RegistrationInfo(registrationGroups: List[RegistrationGroup], registrationPeriods: List[RegistrationPeriod])

case class RegistrationGroup(
  competitionId: String,
  id: String,
  isDefaultGroup: Boolean,
  registrationFee: Option[RegistrationFee],
  categories: Set[String]
)

case class RegistrationFee(currency: String, amount: Int, remainder: Option[Int]) extends Udt
