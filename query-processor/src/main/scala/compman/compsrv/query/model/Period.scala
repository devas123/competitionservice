package compman.compsrv.query.model

import io.getquill.Udt

import java.time.Instant

case class Period(
  comppetitionId: String,
  name: Option[String],
  id: String,
  mats: Seq[Mat],
  startTime: Instant,
  endTime: Instant,
  active: Boolean,
  timeBetweenFights: Int,
  riskCoefficient: Int,
  scheduleEntries: Seq[ScheduleEntry],
  scheduleRequirements: Seq[ScheduleRequirement]
)

case class Mat(id: String, name: Option[String], matOrder: Int) extends Udt