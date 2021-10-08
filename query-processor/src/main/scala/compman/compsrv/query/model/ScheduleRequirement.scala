package compman.compsrv.query.model

import compman.compsrv.model.dto.schedule.ScheduleRequirementType
import io.getquill.Udt

import java.time.Instant

case class ScheduleRequirement(
  id: String,
  competitionId: String,
  categoryIds: Set[String],
  fightIds: Set[String],
  matId: Option[String],
  periodId: Option[String],
  name: Option[String],
  color: Option[String],
  entryType: ScheduleRequirementType,
  force: Boolean,
  startTime: Option[Instant],
  endTime: Option[Instant],
  durationSeconds: Option[Int],
  entryOrder: Option[Int]
) extends Udt
