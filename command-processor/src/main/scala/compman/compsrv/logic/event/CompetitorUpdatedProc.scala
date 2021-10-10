package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitorUpdatedEvent, Event}

object CompetitorUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: CompetitorUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      competitor  <- Option(payload.getFighter)
      competitors <- state.competitors
      newState = state.createCopy(competitors = Some(competitors + (competitor.getId -> competitor)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
