package compman.compsrv.logic.service.schedule

import compman.compsrv.model.dto.schedule.{ScheduleEntryDTO, ScheduleEntryType, ScheduleRequirementDTO}
import compman.compsrv.model.extension._

import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


case class ScheduleAccumulator(initialMatSchedules: List[InternalMatScheduleContainer]) {
  val scheduleEntries: mutable.ArrayBuffer[ScheduleEntryDTO] = ArrayBuffer.empty[ScheduleEntryDTO]
  val matSchedules: mutable.Seq[InternalMatScheduleContainer] = ArrayBuffer.from(initialMatSchedules)
  val invalidFights: mutable.Set[String] = mutable.HashSet[String]()

  def scheduleEntryFromRequirement(requirement: ScheduleRequirementDTO, startTime: Instant, overridePeriodId: Option[String] = None): Int = {
    val periodId = overridePeriodId.orElse(Option(requirement.getPeriodId))
    val index = scheduleEntries.indexWhere { it => it.requirementIdsOrEmpty.contains(requirement.getId) && overridePeriodId.contains(it.getPeriodId) }
    if (index < 0) {
      scheduleEntries.append(new ScheduleEntryDTO()
        .setId(UUID.randomUUID().toString)
        .setPeriodId(periodId.get)
        .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
        .setFightIds(Array.empty)
        .setCategoryIds(Array.empty)
        .setStartTime(startTime)
        .setEndTime(requirement.getEndTime)
        .setRequirementIds(Array(requirement.getId))
        .setName(requirement.getName)
        .setColor(requirement.getColor))
      scheduleEntries.size - 1
    } else {
      index
    }
  }
}