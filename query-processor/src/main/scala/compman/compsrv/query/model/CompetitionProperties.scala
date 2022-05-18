package compman.compsrv.query.model

import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import compservice.model.protobuf.model.CompetitionStatus

import java.util.Date

case class CompetitionProperties(
  id: String,
  creatorId: String,
  staffIds: Option[Set[String]],
  competitionName: String,
  infoTemplate: CompetitionInfoTemplate,
  startDate: Date,
  schedulePublished: Boolean,
  bracketsPublished: Boolean,
  endDate: Option[Date],
  timeZone: String,
  registrationOpen: Boolean,
  creationTimestamp: Date,
  status: CompetitionStatus
)

object CompetitionProperties {
  case class CompetitionInfoTemplate(template: Array[Byte])
}
