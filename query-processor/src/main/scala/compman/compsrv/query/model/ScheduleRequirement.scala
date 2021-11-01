package compman.compsrv.query.model

import compman.compsrv.model.dto.schedule.ScheduleRequirementType

import java.util.Date

case class ScheduleRequirement(
  entryId: String,
  competitionId: String,
  categoryIds: Set[String],
  fightIds: Set[String],
  matId: Option[String],
  periodId: Option[String],
  name: Option[String],
  color: Option[String],
  entryType: ScheduleRequirementType,
  force: Boolean,
  startTime: Option[Date],
  endTime: Option[Date],
  durationSeconds: Option[Int],
  entryOrder: Option[Int]
)
