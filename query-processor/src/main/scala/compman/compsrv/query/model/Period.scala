package compman.compsrv.query.model

import java.time.Instant

case class Period(
  comppetitionId: String,
  name: Option[String],
  id: String,
  startTime: Instant,
  endTime: Instant,
  active: Boolean,
  timeBetweenFights: Int,
  riskCoefficient: Int
)
