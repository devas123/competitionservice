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

class EitherFunctor(private val competitorResults: Array<CompetitorStageResultDTO>,
                    private val fights: List<FightDescriptionDTO>,
                    private val resultOptions: List<FightResultOptionDTO>) : FunctionK<ForCompetitorSelect, EitherPartialOf<CompetitorSelectError>> {
    @Suppress("UNCHECKED_CAST")
    override fun <A> invoke(fa: Kind<ForCompetitorSelect, A>): EitherOf<CompetitorSelectError, A> {
        val g = fa.fix()
        val results = kotlin.runCatching {
            when (g) {
                is CompetitorSelectA.FirstNPlaces -> {
                    right(competitorResults.sortedBy { it.place }.take(g.n).map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.LastNPlaces -> {
                    right(competitorResults.sortedByDescending { it.place }.take(g.n).map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.PassedToRound -> {
                    right(competitorResults.filter { it.round == g.n }.map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.WinnerOfFight -> {
                    Either.monadError<Throwable>().catch {
                        val fight = fights.first { it.id == g.id }
                        val resultDescr = resultOptions.first { it.id == fight.fightResult.resultTypeId }
                        if (resultDescr.isDraw) {
                            throw IllegalArgumentException("No winner in fight ${g.id}")
                        }
                        arrayOf(competitorResults.first { it.competitorId == fight.fightResult.winnerId }.competitorId)
                    }.mapLeft { CompetitorSelectError.NoWinnerOfFight(g.id, it) }.map { it as A }
                }
                is CompetitorSelectA.LoserOfFight -> {
                    Either.monadError<Throwable>().catch {
                        val fight = fights.first { it.id == g.id }
                        val resultDescr = resultOptions.first { it.id == fight.fightResult.resultTypeId }
                        if (resultDescr.isDraw) {
                            throw IllegalArgumentException("No winner in fight ${g.id}")
                        }
                        val loserId = fight.scores.first { it.competitor.id != fight.fightResult.winnerId }.competitor.id
                        arrayOf(competitorResults.first { it.competitorId == loserId }.competitorId)
                    }.mapLeft { CompetitorSelectError.NoWinnerOfFight(g.id, it) }.map { it as A }
                }
                is CompetitorSelectA.And -> {
                    val a = g.a.failFast(competitorResults, fights, resultOptions).map { it as Array<String> }
                    val b = g.b.failFast(competitorResults, fights, resultOptions).map { it as Array<String> }
                    val k = a.flatMap { arr -> b.map { it + arr } }.map { it as A }
                    k
                }
                is CompetitorSelectA.Manual -> {
                    return right(g.ids.toTypedArray() as A)
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
                log.info("Winner of fight ${g.id}")
            }
            is CompetitorSelectA.FirstNPlaces -> {
                log.info("First ${g.n} places ")
            }
            is CompetitorSelectA.LastNPlaces -> {
                log.info("Last ${g.n} places")
            }
            is CompetitorSelectA.LoserOfFight -> {
                log.info("Loser of fight ${g.id}")
            }
            is CompetitorSelectA.PassedToRound -> {
                log.info("Passed to round ${g.n}, ${g.roundType}")
            }
            is CompetitorSelectA.Manual -> {
                log.info("Manual ${g.ids}")
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


fun safeInterpreterEither(competitorResults: Array<CompetitorStageResultDTO>,
                          fights: List<FightDescriptionDTO>,
                          resultOptions: List<FightResultOptionDTO>) = EitherFunctor(competitorResults, fights, resultOptions)
