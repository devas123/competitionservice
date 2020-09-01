package compman.compsrv.repository.collectors

import compman.compsrv.model.dto.schedule.MatIdAndSomeId
import compman.compsrv.model.dto.schedule.ScheduleEntryDTO
import java.time.Instant

class ScheduleEntryAccumulator(private val scheduleEntry: ScheduleEntryDTO) {
    fun getId(): String = scheduleEntry.id
    fun getRequirementIds(): Array<String>? = scheduleEntry.requirementIds
    fun setRequirementIds(reqIds: Array<String>) {
        scheduleEntry.requirementIds = reqIds
    }
    fun getStartTime(): Instant? = scheduleEntry.startTime
    fun setStartTime(time: Instant) { scheduleEntry.startTime = time }
    fun setNumberOfFights(numberOfFights: Int) { scheduleEntry.numberOfFights = numberOfFights }
    private val invalidFightIds = mutableSetOf<String>()
    val categoryIds = mutableSetOf<String>()
    val fightIds = mutableSetOf<MatIdAndSomeId>()
    fun getScheduleEntry(): ScheduleEntryDTO = scheduleEntry
            .setInvalidFightIds(invalidFightIds.toTypedArray())
            .setCategoryIds(categoryIds.toTypedArray())
            .setFightIds(fightIds.toTypedArray())
            .setNumberOfFights(fightIds.size)
}

