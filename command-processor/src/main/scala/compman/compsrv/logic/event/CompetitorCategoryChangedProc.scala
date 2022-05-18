package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorCategoryChangedEvent, Event}

object CompetitorCategoryChangedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = {
    case x: CompetitorCategoryChangedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitorCategoryChangedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      fighterId <- Option(payload.fighterId)
      updatedCategories <- Option(payload.newCategories)
      competitor <- state.competitors.flatMap(_.get(fighterId))
      newCompetitor = competitor.copy(categories = updatedCategories)
      newState = state.copy(competitors = state.competitors.map(_ + (competitor.id -> newCompetitor)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
