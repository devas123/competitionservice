package compman.compsrv.query.model

import io.getquill.Udt

import java.util.Date

case class Period(
  competitionId: String,
  name: Option[String],
  id: String,
  mats: List[Mat],
  startTime: Option[Date],
  endTime: Option[Date],
  active: Boolean,
  timeBetweenFights: Int,
  riskCoefficient: Int,
  scheduleEntries: List[ScheduleEntry],
  scheduleRequirements: List[ScheduleRequirement]
)

case class Mat(matId: String, name: String, matOrder: Int) extends Udt
