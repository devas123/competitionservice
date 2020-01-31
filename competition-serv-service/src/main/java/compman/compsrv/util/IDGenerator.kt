package compman.compsrv.util

import com.google.common.hash.Hashing
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.jpa.competition.CategoryRestriction
import compman.compsrv.jpa.competition.CompScore
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import java.util.*
import kotlin.random.Random

object IDGenerator {
    private const val SALT = "zhenekpenek"
    private val random = Random(System.currentTimeMillis())
    fun uid() = "${UUID.randomUUID()}-${System.currentTimeMillis()}"
    fun luid() = random.nextLong()
    fun restrictionId(restriction: CategoryRestriction) = restriction.id
            ?: hashString("${restriction.name}/${restriction.minValue}/${restriction.maxValue}")

    fun restrictionId(restriction: CategoryRestrictionDTO) = restriction.id
            ?: hashString("${restriction.name}/${restriction.minValue}/${restriction.maxValue}")

    fun categoryId(category: CategoryDescriptorDTO) = category.id ?: hashString("${
    category.restrictions?.fold(StringBuilder()) { acc, r ->
        acc.append(restrictionId(r))
    }
    }/${category.fightDuration}")

    fun categoryId(category: CategoryDescriptor) = category.id ?: hashString("${
    category.restrictions?.fold(StringBuilder()) { acc, r ->
        acc.append(restrictionId(r))
    }
    }/${category.fightDuration}")

    fun compScoreId(competitorId: String) = "${competitorId}_${UUID.randomUUID()}"

    fun hashString(str: String) = Hashing.sha256().hashBytes("$SALT$str".toByteArray(Charsets.UTF_8)).toString()
    fun fightId(competitionId: String, categoryId: String?, stageId: String, rount: Int, number: Int, roundType: StageRoundType?) = hashString("$competitionId-$categoryId-$rount-$number-$stageId-$roundType")
    fun stageId(competitionId: String, categoryId: String?, stageName: String?, stageOrder: Int) = hashString("$competitionId-$categoryId-$stageName-$stageOrder")
    fun compResultId(competitorId: String, stageId: String, competitionId: String): String = hashString(competitorId + stageId + competitionId)
}