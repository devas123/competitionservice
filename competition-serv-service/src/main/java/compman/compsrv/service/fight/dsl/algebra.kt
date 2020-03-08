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
import arrow.core.*
import arrow.core.extensions.either.monad.monad
import arrow.free.Free
import arrow.free.foldMap
import arrow.higherkind
import compman.compsrv.model.dto.brackets.CompetitorStageResultDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO

class ForCompetitorSelect private constructor() {
    companion object
}

@higherkind
sealed class CompetitorSelectA<out A> : CompetitorSelectKind<A> {
    data class FirstNPlaces<A>(val n: Int) : CompetitorSelectA<Array<A>>()
    data class LastNPlaces<A>(val n: Int) : CompetitorSelectA<Array<A>>()
    data class WinnerOfFight<A>(val id: String) : CompetitorSelectA<Option<A>>()
    data class LoserOfFight<A>(val id: String) : CompetitorSelectA<Option<A>>()
    data class PassedToRound<A>(val n: Int) : CompetitorSelectA<Array<A>>()
    data class And<A>(val a: CompetitorSelect<A>, val b: CompetitorSelect<A>) : CompetitorSelectA<A>()
}

typealias CompetitorSelectKind<A> = Kind<ForCompetitorSelect, A>

@Suppress("NOTHING_TO_INLINE")
inline fun <A> CompetitorSelectKind<A>.fix(): CompetitorSelectA<A> =
        this as CompetitorSelectA<A>

typealias CompetitorSelect<A> = Free<ForCompetitorSelect, A>

inline fun <reified A> firstNPlaces(n: Int): CompetitorSelect<Array<A>> = Free.liftF(CompetitorSelectA.FirstNPlaces(n))
inline fun <reified A> lastNPlaces(n: Int): CompetitorSelect<Array<A>> = Free.liftF(CompetitorSelectA.LastNPlaces(n))
inline fun <reified A> winnerOfFight(id: String): CompetitorSelect<Option<A>> = Free.liftF(CompetitorSelectA.WinnerOfFight(id))
inline fun <reified A> loserOfFight(id: String): CompetitorSelect<Option<A>> = Free.liftF(CompetitorSelectA.LoserOfFight(id))
inline fun <reified A> passedToRound(n: Int): CompetitorSelect<Array<A>> = Free.liftF(CompetitorSelectA.PassedToRound(n))
inline fun <reified A> and(a: CompetitorSelect<A>, b: CompetitorSelect<A>): CompetitorSelect<A> =
        Free.liftF(CompetitorSelectA.And(a, b))


fun <A> CompetitorSelect<A>.failFast(competitorResults: Array<CompetitorStageResultDTO>,
                                     fights: List<FightDescriptionDTO>,
                                     resultOptions: List<FightResultOptionDTO>): Either<CompetitorSelectError, A> {
    return foldMap(safeInterpreterEither(competitorResults, fights, resultOptions), Either.monad()).fix()
}
