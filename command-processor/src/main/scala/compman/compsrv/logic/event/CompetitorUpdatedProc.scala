package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorUpdatedEvent, Event}

object CompetitorUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: CompetitorUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      competitor  <- payload.competitor
      competitors <- state.competitors
      newState = state.copy(competitors = Some(competitors + (competitor.id -> competitor)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
