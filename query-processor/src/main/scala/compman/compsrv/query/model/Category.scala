package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.CategoryRestrictionType
import io.getquill.Udt

case class Category(
  id: String,
  competitionId: String,
  restrictions: Set[Restriction],
  name: Option[String],
  registrationOpen: Boolean
)

case class Restriction(
  id: String,
  `type`: CategoryRestrictionType,
  name: String,
  value: String,
  alias: String,
  minValue: String,
  maxValue: String,
  unit: String,
  restrictionOrder: Int
) extends Udt
