package compman.compsrv.service.schedule.internal

import compman.compsrv.model.dto.schedule.ScheduleEntryDTO
import compman.compsrv.model.dto.schedule.ScheduleEntryType
import compman.compsrv.model.dto.schedule.ScheduleRequirementDTO
import compman.compsrv.repository.collectors.ScheduleEntryAccumulator
import compman.compsrv.util.IDGenerator
import java.time.Instant

class ScheduleAccumulator(initialMatSchedules: List<InternalMatScheduleContainer>, private val competitionId: String) {
    val scheduleEntries = mutableListOf<ScheduleEntryAccumulator>()
    val matSchedules = ArrayList(initialMatSchedules)
    val invalidFights = HashSet<String>()

    fun scheduleEntryFromRequirement(requirement: ScheduleRequirementDTO, startTime: Instant, overridePeriodId: String = requirement.periodId): Int {
        val index = scheduleEntries.indexOfFirst { it.getRequirementIds()?.contains(requirement.id) == true && it.getPeriodId() == overridePeriodId }
                return if (index < 0) {
                    scheduleEntries.add(ScheduleEntryAccumulator(ScheduleEntryDTO()
                            .setId(IDGenerator
                                    .scheduleEntryId(competitionId, overridePeriodId))
                            .setPeriodId(overridePeriodId)
                            .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                            .setFightIds(emptyArray())
                            .setCategoryIds(emptyArray())
                            .setStartTime(startTime)
                            .setEndTime(requirement.endTime)
                            .setRequirementIds(arrayOf(requirement.id))
                            .setName(requirement.name)
                            .setColor(requirement.color))
                    )
                    scheduleEntries.size - 1
                } else {
                    index
                }
    }
}