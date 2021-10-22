package compman.compsrv.query.model

import java.util.Date

case class FightByScheduleEntry(
  scheduleEntryId: String,
  categoryId: String,
  periodId: String,
  competitionId: String,
  matId: Option[String],
  fightId: String,
  startTime: Option[Date]
)
