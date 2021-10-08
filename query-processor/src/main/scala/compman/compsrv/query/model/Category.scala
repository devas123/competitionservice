package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.CategoryRestrictionType
import io.getquill.Udt

case class Category(
  id: String,
  competitionId: String,
  restrictions: Option[Set[Restriction]],
  name: Option[String],
  registrationOpen: Boolean
)

case class Restriction(
  id: String,
  `type`: CategoryRestrictionType,
  name: Option[String],
  value: Option[String],
  alias: Option[String],
  minValue: Option[String],
  maxValue: Option[String],
  unit: Option[String],
  restrictionOrder: Int
) extends Udt

