package compman.compsrv.service.fight.dsl

import arrow.Kind
import arrow.core.Either
import arrow.core.Id
import arrow.core.extensions.either.monad.monad
import arrow.core.extensions.id.monad.monad
import arrow.core.fix
import arrow.free.Free
import arrow.free.foldMap
import arrow.higherkind
import compman.compsrv.model.dto.brackets.CompetitorStageResultDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import org.slf4j.Logger

class ForCompetitorSelect private constructor() {
    companion object
}
typealias CompetitorSelectAOf<A> = Kind<ForCompetitorSelect, A>

@Suppress("NOTHING_TO_INLINE")
inline fun <A> CompetitorSelectAOf<A>.fix(): CompetitorSelectA<A> =
        this as CompetitorSelectA<A>

typealias CompetitorSelect<A> = Free<ForCompetitorSelect, A>

@higherkind
sealed class CompetitorSelectA<out A> : CompetitorSelectAOf<A> {
    data class FirstNPlaces(val stageId: String, val n: Int) : CompetitorSelectA<Array<String>>()
    data class LastNPlaces(val stageId: String, val n: Int) : CompetitorSelectA<Array<String>>()
    data class WinnerOfFight(val stageId: String, val id: String) : CompetitorSelectA<Array<String>>()
    data class LoserOfFight(val stageId: String, val id: String) : CompetitorSelectA<Array<String>>()
    data class PassedToRound(val stageId: String, val n: Int, val roundType: StageRoundType) : CompetitorSelectA<Array<String>>()
    data class Manual(val stageId: String, val ids: Collection<String>) : CompetitorSelectA<Array<String>>()
    data class And<A>(val a: CompetitorSelect<A>, val b: CompetitorSelect<A>) : CompetitorSelectA<A>()
}



fun firstNPlaces(stageId: String, n: Int): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.FirstNPlaces(stageId, n))
fun lastNPlaces(stageId: String, n: Int): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.LastNPlaces(stageId, n))
fun manual(stageId: String, ids: Collection<String>): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.Manual(stageId, ids))
//fun winnerOfFight(stageId: String, id: String): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.WinnerOfFight(stageId, id))
//fun loserOfFight(stageId: String, id: String): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.LoserOfFight(stageId, id))
fun passedToRound(stageId: String, n: Int, roundType: StageRoundType): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.PassedToRound(stageId, n, roundType))
inline fun <reified A> and(a: CompetitorSelect<A>, b: CompetitorSelect<A>): CompetitorSelect<A> =
        Free.liftF(CompetitorSelectA.And(a, b))

fun <A> combine(a: CompetitorSelect<A>, b: CompetitorSelect<A>): CompetitorSelect<A> = Free.liftF(CompetitorSelectA.And(a, b))

operator fun <A> CompetitorSelect<A>.plus(other: CompetitorSelect<A>): CompetitorSelect<A> =
        combine(this, other)

fun <A> CompetitorSelect<A>.failFast(competitorResults: (stageId: String) -> List<CompetitorStageResultDTO>,
                                     fights: (stageId: String) -> List<FightDescriptionDTO>,
                                     resultOptions: (stageId: String) -> List<FightResultOptionDTO>): Either<CompetitorSelectError, A> {
    return foldMap(safeInterpreterEither(competitorResults, fights, resultOptions), Either.monad()).fix()
}

fun <A> CompetitorSelect<A>.log(log: Logger): Id<A> {
    return foldMap(LoggingInterpreter(log), Id.monad()).fix()
}
