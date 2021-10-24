package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorCategoryChangedEvent, Event}
import compman.compsrv.model.extensions.CompetitorOps
import compman.compsrv.model.{CompetitionState, Payload}

object CompetitorCategoryChangedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitorCategoryChangedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitorCategoryChangedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      fighterId <- Option(payload.getFighterId)
      newCategoryId <- Option(payload.getNewCategoryId)
      oldCategoryOpt = Option(payload.getOldCategoryId)
      competitor <- state.competitors.flatMap(_.get(fighterId))
      updatedCategories = (oldCategoryOpt.map(c => competitor.getCategories.filter(_ != c)).getOrElse(competitor.getCategories) :+ newCategoryId).distinct
      newCompetitor = competitor.copy(categories = updatedCategories)
      newState = state.createCopy(competitors = state.competitors.map(_ + (competitor.getId -> newCompetitor)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
