package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorCategoryChangedEvent, Event}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object CompetitorCategoryChangedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: CompetitorCategoryChangedEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorCategoryChangedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload           <- event.payload
      fighterId         <- Option(payload.fighterId)
      updatedCategories <- Option(payload.newCategories)
      competitor        <- state.competitors.get(fighterId)
      newCompetitor = competitor.copy(categories = updatedCategories)
      newState      = state.copy(competitors = state.competitors + (competitor.id -> newCompetitor))
    } yield newState
    Monad[F].pure(eventT)
  }
}
