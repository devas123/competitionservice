package compman.compsrv.util

import com.google.common.hash.Hashing
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.model.dto.schedule.ScheduleRequirementType
import java.util.*

object IDGenerator {
    private const val SALT = "zhenekpenek"
    fun uid() = "${UUID.randomUUID()}-${System.currentTimeMillis()}"
    fun restrictionId(restriction: CategoryRestrictionDTO) = restriction.id
            ?: hashString("${restriction.name}/${restriction.minValue}/${restriction.maxValue}")

    fun categoryId(category: CategoryDescriptorDTO) = category.id ?: hashString("${
    category.restrictions?.fold(StringBuilder()) { acc, r ->
        acc.append(restrictionId(r))
    }
    }/${category.fightDuration}")

    fun hashString(str: String) = Hashing.sha256().hashBytes("$SALT$str".toByteArray(Charsets.UTF_8)).toString()
    fun fightId(stageId: String, groupId: String? = "") = hashString("$stageId-$groupId-${UUID.randomUUID()}")
    fun stageId(competitionId: String, categoryId: String) = hashString("$competitionId-$categoryId-${UUID.randomUUID()}")
    fun compResultId(competitorId: String, stageId: String, competitionId: String): String = hashString(competitorId + stageId + competitionId)
    fun createPeriodId(competitionId: String) = hashString("$competitionId-${UUID.randomUUID()}")
    fun createMatId(periodId: String) = hashString("$periodId-${UUID.randomUUID()}")
    fun scheduleEntryId(competitionId: String, periodId: String): String = hashString("$competitionId-$periodId-${UUID.randomUUID()}")
    fun scheduleRequirementId(competitionId: String, periodId: String, entryType: ScheduleRequirementType): String = hashString("$competitionId-$periodId-${entryType}-${UUID.randomUUID()}")
    fun groupId(stageId: String): String =
            hashString("$stageId-${UUID.randomUUID()}")

}