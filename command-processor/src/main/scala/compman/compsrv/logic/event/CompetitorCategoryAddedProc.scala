package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorCategoryAddedEvent, Event}

object CompetitorCategoryAddedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: CompetitorCategoryAddedEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorCategoryAddedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload       <- event.payload
      fighterId     <- Option(payload.fighterId)
      newCategoryId <- Option(payload.newCategoryId)
      competitor    <- state.competitors.get(fighterId)
      updatedCategories = (competitor.categories :+ newCategoryId).distinct
      newCompetitor     = competitor.copy(categories = updatedCategories)
      newState          = state.copy(competitors = state.competitors + (competitor.id -> newCompetitor))
    } yield newState
    Monad[F].pure(eventT)
  }
}
