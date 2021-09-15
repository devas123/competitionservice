package compman.compsrv.query.model

import io.getquill.Udt

case class RegistrationGroup(
  competitionId: String,
  id: String,
  isDefaultGroup: Boolean,
  registrationFee: RegistrationFee,
  categories: Set[String]
)

case class RegistrationFee(currency: String, amount: Int, remainder: Option[Int]) extends Udt
