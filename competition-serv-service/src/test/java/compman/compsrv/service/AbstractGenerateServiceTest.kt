package compman.compsrv.service

import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.util.copy
import java.math.BigDecimal
import java.util.*
import kotlin.random.Random

open class AbstractGenerateServiceTest {
    companion object {
        val fightResultOptions = FightResultOptionDTO.values.map { it.setId(UUID.randomUUID().toString())}
        fun generateFightResult(fight: FightDescriptionDTO): Pair<FightDescriptionDTO, CompetitorDTO?> {
            val scores = fight.scores?.toList()
            val competitor = when (scores?.size) {
                2 -> {
                    scores[Random.nextInt(2)].competitor
                }
                1 -> {
                    scores.first().competitor
                }
                else -> {
                    null
                }
            }
            return fight.copy(fightResult = competitor?.let { FightResultDTO(it.id, fightResultOptions[Random.nextInt(3)].id, "bla bla bla") }) to competitor
        }
    }
    protected val duration: BigDecimal = BigDecimal.valueOf(8)
    protected val competitionId = "UG9wZW5nYWdlbiBPcGVu"
    protected val categoryId = "UG9wZW5nYWdlbiBPcGVu-UG9wZW5nYWdlbiBPcGVu"
    protected val stageId = "asoifjqwoijqwoijqpwtoj2j12-j1fpasoj"
    val category = CategoryGeneratorService.createCategory(8, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
}