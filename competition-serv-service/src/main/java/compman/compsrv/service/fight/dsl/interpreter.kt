///*
// * Copyright (C) 2017 Pablo Guardiola Sánchez.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
package compman.compsrv.service.fight.dsl

import arrow.Kind
import arrow.core.*
import arrow.core.Either.Companion.right
import arrow.core.extensions.either.applicativeError.catch
import arrow.core.extensions.either.monadError.monadError
import compman.compsrv.model.dto.brackets.CompetitorStageResultDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO

class EitherFunctor(private val competitorResults: Array<CompetitorStageResultDTO>,
                    private val fights: List<FightDescriptionDTO>,
                    private val resultOptions: List<FightResultOptionDTO>) : FunctionK<ForCompetitorSelect, EitherPartialOf<CompetitorSelectError>> {
    @Suppress("UNCHECKED_CAST")
    override fun <A> invoke(fa: Kind<ForCompetitorSelect, A>): EitherOf<CompetitorSelectError, A> {
        val g = fa.fix()
        val results = kotlin.runCatching {
            when (g) {
                is CompetitorSelectA.FirstNPlaces<*> -> {
                    right(competitorResults.sortedBy { it.place }.take(g.n).map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.LastNPlaces<*> -> {
                    right(competitorResults.sortedByDescending { it.place }.take(g.n).map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.PassedToRound<*> -> {
                    right(competitorResults.filter { it.round == g.n }.map { e -> e.competitorId }.toTypedArray()).map { it as A }
                }
                is CompetitorSelectA.WinnerOfFight<*> -> {
                    Either.monadError<Throwable>().catch {
                        val fight = fights.first { it.id == g.id }
                        val resultDescr = resultOptions.first { it.id == fight.fightResult.resultTypeId }
                        if (resultDescr.isDraw) {
                            throw IllegalArgumentException("No winner in fight ${g.id}")
                        }
                        competitorResults.first { it.competitorId == fight.fightResult.winnerId }
                    }.mapLeft { CompetitorSelectError.NoWinnerOfFight(g.id, it) }.map { it as A }
                }
                is CompetitorSelectA.LoserOfFight<*> -> {
                    Either.monadError<Throwable>().catch {
                        val fight = fights.first { it.id == g.id }
                        val resultDescr = resultOptions.first { it.id == fight.fightResult.resultTypeId }
                        if (resultDescr.isDraw) {
                            throw IllegalArgumentException("No winner in fight ${g.id}")
                        }
                        val loserId = fight.scores.first { it.competitor.id != fight.fightResult.winnerId }.competitor.id
                        competitorResults.first { it.competitorId == loserId }
                    }.mapLeft { CompetitorSelectError.NoWinnerOfFight(g.id, it) }.map { it as A }
                }
                is CompetitorSelectA.And<*> -> {
                    val a = g.a.failFast(competitorResults, fights, resultOptions).map { it as A }
                    val b = g.b.failFast(competitorResults, fights, resultOptions).map { it as A }
                    val k = a.combineK(b)
                    k
                }
            }
        }
        return results.fold({ it }, { Either.left(CompetitorSelectError.UnknownError(it.message ?: "", it)) })
    }
}

fun safeInterpreterEither(competitorResults: Array<CompetitorStageResultDTO>,
                          fights: List<FightDescriptionDTO>,
                          resultOptions: List<FightResultOptionDTO>) = EitherFunctor(competitorResults, fights, resultOptions)
