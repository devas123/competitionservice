package compman.compsrv.query.model

import compman.compsrv.model.dto.schedule.ScheduleRequirementType

import java.time.Instant

case class ScheduleRequirement(
  id: String,
  competitionId: String,
  categoryIds: Set[String],
  fightIds: Set[String],
  matId: String,
  periodId: String,
  name: String,
  color: String,
  entryType: ScheduleRequirementType,
  force: Boolean,
  startTime: Instant,
  endTime: Instant,
  durationSeconds: Int,
  entryOrder: Int
)
