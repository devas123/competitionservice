package compman.compsrv.service.fight.dsl

import arrow.Kind
import arrow.core.*
import arrow.core.Either.Companion.right
import arrow.core.extensions.either.applicativeError.catch
import arrow.core.extensions.either.monadError.monadError
import compman.compsrv.model.dto.brackets.CompetitorStageResultDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.slf4j.Logger

class EitherFunctor(private val competitorResults: (stageId: String) -> List<CompetitorStageResultDTO>,
                    private val fights: (stageId: String) -> List<FightDescriptionDTO>,
                    private val resultOptions: (stageId: String) -> List<FightResultOptionDTO>) : FunctionK<ForCompetitorSelect, EitherPartialOf<CompetitorSelectError>> {
    @Suppress("UNCHECKED_CAST")
    override fun <A> invoke(fa: Kind<ForCompetitorSelect, A>): EitherOf<CompetitorSelectError, A> {
        val g = fa.fix()
        val results = kotlin.runCatching {
            when (g) {
                is CompetitorSelectA.FirstNPlaces -> {
                    right(competitorResults(g.stageId).sortedBy { it.place }.take(g.n).map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.LastNPlaces -> {
                    right(competitorResults(g.stageId).sortedByDescending { it.place }.take(g.n).map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.PassedToRound -> {
                    right(competitorResults(g.stageId).filter { it.round == g.n }.map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.WinnerOfFight -> {
                    Either.monadError<Throwable>().catch {
                        val fight = fights(g.stageId).first { it.id == g.id }
                        val resultDescr = resultOptions(g.stageId).first { it.id == fight.fightResult.resultTypeId }
                        if (resultDescr.isDraw) {
                            throw IllegalArgumentException("No winner in fight ${g.id}")
                        }
                        arrayOf(competitorResults(g.stageId).first { it.competitorId == fight.fightResult.winnerId }.competitorId)
                    }.mapLeft { CompetitorSelectError.NoWinnerOfFight(g.id, it) }.map { it as A }
                }
                is CompetitorSelectA.LoserOfFight -> {
                    Either.monadError<Throwable>().catch {
                        val fight = fights(g.stageId).first { it.id == g.id }
                        val resultDescr = resultOptions(g.stageId).first { it.id == fight.fightResult.resultTypeId }
                        if (resultDescr.isDraw) {
                            throw IllegalArgumentException("No loser in fight ${g.id}")
                        }
                        val loserId = fight.scores.first { it.competitor.id != fight.fightResult.winnerId }.competitor.id
                        arrayOf(competitorResults(g.stageId).first { it.competitorId == loserId }.competitorId)
                    }.mapLeft { CompetitorSelectError.NoLoserOfFight(g.id, it) }.map { it as A }
                }
                is CompetitorSelectA.And -> {
                    val a = g.a.failFast(competitorResults, fights, resultOptions).map { it as Array<String> }
                    val b = g.b.failFast(competitorResults, fights, resultOptions).map { it as Array<String> }
                    val k = a.flatMap { arr -> b.map { it + arr } }.map { it as A }
                    k
                }
                is CompetitorSelectA.Manual -> {
                    return if (!g.ids.isEmpty()) {
                         right(g.ids.toTypedArray() as A)
                    } else { Either.left(CompetitorSelectError.NoCompetitorsSelected(g.ids)) }
                }
            }
        }
        return results.fold({ it }, { Either.left(CompetitorSelectError.UnknownError(it.message ?: "", it)) })
    }
}

class LoggingInterpreter(private val log: Logger) : FunctionK<ForCompetitorSelect, ForId> {
    @Suppress("UNCHECKED_CAST")
    override fun <A> invoke(fa: CompetitorSelectAOf<A>): IdOf<A> {
        when (val g = fa.fix()) {
            is CompetitorSelectA.WinnerOfFight -> {
                log.info("Winner of fight ${g.id}, stage: ${g.stageId}")
            }
            is CompetitorSelectA.FirstNPlaces -> {
                log.info("First ${g.n} places, stage: ${g.stageId}")
            }
            is CompetitorSelectA.LastNPlaces -> {
                log.info("Last ${g.n} places, stage: ${g.stageId}")
            }
            is CompetitorSelectA.LoserOfFight -> {
                log.info("Loser of fight ${g.id}, stage: ${g.stageId}")
            }
            is CompetitorSelectA.PassedToRound -> {
                log.info("Passed to round ${g.n}, ${g.roundType}, stage: ${g.stageId}")
            }
            is CompetitorSelectA.Manual -> {
                log.info("Manual ${g.ids}, stage: ${g.stageId}")
            }
            is CompetitorSelectA.And -> {
                g.a.log(log)
                log.info("And")
                g.b.log(log)
            }
        }
        return Id.just(arrayOf<String>()) as IdOf<A>
    }
}


fun safeInterpreterEither(competitorResults: (stageId: String) -> List<CompetitorStageResultDTO>,
                          fights: (stageId: String) -> List<FightDescriptionDTO>,
                          resultOptions: (stageId: String) -> List<FightResultOptionDTO>) = EitherFunctor(competitorResults, fights, resultOptions)
