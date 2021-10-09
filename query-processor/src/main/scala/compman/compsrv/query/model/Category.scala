package compman.compsrv.query.model

import compman.compsrv.model.dto.competition.CategoryRestrictionType
import io.getquill.Udt

case class Category(
  id: String,
  competitionId: String,
  restrictions: List[Restriction],
  name: Option[String],
  registrationOpen: Boolean
)

case class Restriction(
                        restrictionId: String,
                        restrictionType: CategoryRestrictionType,
                        name: Option[String],
                        value: Option[String],
                        alias: Option[String],
                        minValue: Option[String],
                        maxValue: Option[String],
                        unit: Option[String],
                        restrictionOrder: Int
) extends Udt
