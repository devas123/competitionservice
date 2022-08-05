package compman.compsrv.query.model

import compservice.model.protobuf.model.CompetitionStatus

import java.util.Date

case class CompetitionProperties(
  id: String,
  creatorId: String,
  staffIds: Option[Set[String]],
  competitionName: String,
  startDate: Date,
  schedulePublished: Boolean,
  bracketsPublished: Boolean,
  endDate: Option[Date],
  timeZone: String,
  creationTimestamp: Date,
  status: CompetitionStatus
)