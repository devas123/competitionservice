package compman.compsrv.logic.schedule

import compman.compsrv.model.extensions._
import compservice.model.protobuf.model._

import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


case class ScheduleAccumulator(initialMatSchedules: List[InternalMatScheduleContainer]) {
  val scheduleEntries: mutable.ArrayBuffer[ScheduleEntry] = ArrayBuffer.empty[ScheduleEntry]
  val matSchedules: mutable.Seq[InternalMatScheduleContainer] = ArrayBuffer.from(initialMatSchedules)
  val invalidFights: mutable.Set[String] = mutable.HashSet[String]()

  def scheduleEntryFromRequirement(requirement: ScheduleRequirement, startTime: Instant, overridePeriodId: Option[String] = None): Int = {
    val periodId = overridePeriodId.orElse(Option(requirement.periodId))
    val index = scheduleEntries.indexWhere { it => it.requirementIdsOrEmpty.contains(requirement.id) && overridePeriodId.contains(it.periodId) }
    if (index < 0) {
      scheduleEntries.append(ScheduleEntry()
        .withId(UUID.randomUUID().toString)
        .withPeriodId(periodId.get)
        .withEntryType(ScheduleEntryType.FIGHTS_GROUP)
        .withFightScheduleInfo(Seq.empty)
        .withCategoryIds(Seq.empty)
        .withStartTime(startTime.asTimestamp)
        .withEndTime(requirement.getEndTime)
        .withRequirementIds(Seq(requirement.id))
        .withName(requirement.getName)
        .withColor(requirement.getColor))
      scheduleEntries.size - 1
    } else {
      index
    }
  }
}