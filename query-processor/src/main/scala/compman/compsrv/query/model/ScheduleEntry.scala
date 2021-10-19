package compman.compsrv.query.model

import compman.compsrv.model.dto.schedule.ScheduleEntryType
import io.getquill.Udt

import java.time.Instant

case class ScheduleEntry(
                          entryId: String,
                          competitionId: String,
                          categoryIds: Set[String],
                          fightIds: List[MatIdAndSomeId],
                          periodId: String,
                          description: Option[String],
                          name: Option[String],
                          color: Option[String],
                          entryType: ScheduleEntryType,
                          requirementIds: Set[String],
                          startTime: Option[Instant],
                          endTime: Option[Instant],
                          numberOfFights: Option[Int],
                          entryDuration: Option[Int],
                          entryOrder: Int
) extends Udt
