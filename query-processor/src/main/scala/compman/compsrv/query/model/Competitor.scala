package compman.compsrv.query.model

import io.getquill.Udt

import java.time.Instant

case class Competitor(
  competitionId: String,
  userId: Option[String],
  email: String,
  id: String,
  firstName: String,
  lastName: String,
  birthDate: Option[Instant],
  academy: Option[Academy],
  categories: Set[String],
  isPlaceholder: Boolean,
  promo: Option[String]
)

case class Academy(id: String, name: String) extends Udt
