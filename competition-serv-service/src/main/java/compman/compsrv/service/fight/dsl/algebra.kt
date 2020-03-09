/*
 * Copyright (C) 2017 Pablo Guardiola Sánchez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

@higherkind
sealed class CompetitorSelectA<out A> : CompetitorSelectAOf<A> {
    data class FirstNPlaces(val n: Int) : CompetitorSelectA<Array<String>>()
    data class LastNPlaces(val n: Int) : CompetitorSelectA<Array<String>>()
    data class WinnerOfFight(val id: String) : CompetitorSelectA<Array<String>>()
    data class LoserOfFight(val id: String) : CompetitorSelectA<Array<String>>()
    data class PassedToRound(val n: Int, val roundType: StageRoundType) : CompetitorSelectA<Array<String>>()
    data class Manual(val ids: List<String>) : CompetitorSelectA<Array<String>>()
    data class And<A>(val a: CompetitorSelect<A>, val b: CompetitorSelect<A>) : CompetitorSelectA<A>()
}

typealias CompetitorSelectAOf<A> = Kind<ForCompetitorSelect, A>

@Suppress("NOTHING_TO_INLINE")
inline fun <A> CompetitorSelectAOf<A>.fix(): CompetitorSelectA<A> =
        this as CompetitorSelectA<A>

typealias CompetitorSelect<A> = Free<ForCompetitorSelect, A>

fun firstNPlaces(n: Int): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.FirstNPlaces(n))
fun lastNPlaces(n: Int): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.LastNPlaces(n))
fun manual(ids: List<String>): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.Manual(ids))
fun winnerOfFight(id: String): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.WinnerOfFight(id))
fun loserOfFight(id: String): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.LoserOfFight(id))
fun passedToRound(n: Int, roundType: StageRoundType): CompetitorSelect<Array<String>> = Free.liftF(CompetitorSelectA.PassedToRound(n, roundType))
inline fun <reified A> and(a: CompetitorSelect<A>, b: CompetitorSelect<A>): CompetitorSelect<A> =
        Free.liftF(CompetitorSelectA.And(a, b))

fun <A> combine(a: CompetitorSelect<A>, b: CompetitorSelect<A>): CompetitorSelect<A> = Free.liftF(CompetitorSelectA.And(a, b))

operator fun <A> CompetitorSelect<A>.plus(other: CompetitorSelect<A>): CompetitorSelect<A> =
        combine(this, other)

fun <A> CompetitorSelect<A>.failFast(competitorResults: Array<CompetitorStageResultDTO>,
                                     fights: List<FightDescriptionDTO>,
                                     resultOptions: List<FightResultOptionDTO>): Either<CompetitorSelectError, A> {
    return foldMap(safeInterpreterEither(competitorResults, fights, resultOptions), Either.monad()).fix()


}

fun <A> CompetitorSelect<A>.log(log: Logger): Id<A> {
    return foldMap(LoggingInterpreter(log), Id.monad()).fix()
}
