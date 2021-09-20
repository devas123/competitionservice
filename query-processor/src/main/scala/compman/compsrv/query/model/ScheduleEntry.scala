package compman.compsrv.query.model

import compman.compsrv.model.dto.schedule.ScheduleEntryType
import io.getquill.Udt

import java.time.Instant

case class ScheduleEntry(
  id: String,
  competitionId: String,
  categoryIds: Set[String],
  fightIds: List[MatIdAndSomeId],
  periodId: String,
  description: String,
  name: String,
  color: String,
  entryType: ScheduleEntryType,
  requirementIds: Set[String],
  startTime: Instant,
  endTime: Instant,
  numberOfFights: Int,
  duration: Int,
  order: Int
) extends Udt