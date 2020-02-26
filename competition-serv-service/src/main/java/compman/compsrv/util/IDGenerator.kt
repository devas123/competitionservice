package compman.compsrv.util

import com.google.common.hash.Hashing
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.model.dto.schedule.ScheduleEntryType
import compman.compsrv.model.dto.schedule.ScheduleRequirementType
import java.util.*
import kotlin.random.Random

object IDGenerator {
    private const val SALT = "zhenekpenek"
    private val random = Random(System.currentTimeMillis())
    fun uid() = "${UUID.randomUUID()}-${System.currentTimeMillis()}"
    fun restrictionId(restriction: CategoryRestrictionDTO) = restriction.id
            ?: hashString("${restriction.name}/${restriction.minValue}/${restriction.maxValue}")

    fun categoryId(category: CategoryDescriptorDTO) = category.id ?: hashString("${
    category.restrictions?.fold(StringBuilder()) { acc, r ->
        acc.append(restrictionId(r))
    }
    }/${category.fightDuration}")

    fun hashString(str: String) = Hashing.sha256().hashBytes("$SALT$str".toByteArray(Charsets.UTF_8)).toString()
    fun fightId(competitionId: String, categoryId: String?, stageId: String, rount: Int, number: Int, roundType: StageRoundType?) = hashString("$competitionId-$categoryId-$rount-$number-$stageId-$roundType")
    fun stageId(competitionId: String, categoryId: String?, stageName: String?, stageOrder: Int) = hashString("$competitionId-$categoryId-$stageName-$stageOrder")
    fun compResultId(competitorId: String, stageId: String, competitionId: String): String = hashString(competitorId + stageId + competitionId)
    fun createPeriodId(competitionId: String) = hashString("$competitionId-period-${UUID.randomUUID()}")
    fun createMatId(periodId: String, matNumber: Int) = hashString("$periodId-mat-$matNumber")
    fun scheduleEntryId(competitionId: String, periodId: String, index: Int, entryType: ScheduleEntryType): String =         hashString("$competitionId-$periodId-scheduleEntry-${entryType}-$index")
    fun scheduleRequirementId(competitionId: String, periodId: String, index: Int, entryType: ScheduleRequirementType): String =         hashString("$competitionId-$periodId-scheduleRequirement-${entryType}-$index")

}