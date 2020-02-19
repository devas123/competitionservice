package compman.compsrv.service

import compman.compsrv.model.dto.brackets.CompetitorResultType
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.util.copy
import java.math.BigDecimal
import kotlin.random.Random

open class AbstractGenerateServiceTest {
    companion object {
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
            return fight.copy(fightResult = competitor?.let { FightResultDTO(it.id, CompetitorResultType.values()[Random.nextInt(3)], "bla bla bla") }) to competitor
        }
    }
    protected val duration: BigDecimal = BigDecimal.valueOf(8)
    protected val competitionId = "UG9wZW5nYWdlbiBPcGVu"
    protected val categoryId = "UG9wZW5nYWdlbiBPcGVu-UG9wZW5nYWdlbiBPcGVu"
    protected val stageId = "asoifjqwoijqwoijqpwtoj2j12-j1fpasoj"
    val category = CategoryGeneratorService.createCategory(8, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
}