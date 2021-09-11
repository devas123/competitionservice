package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitorUpdatedEvent, Event}

object CompetitorUpdatedProc {
  def apply[F[+_]: Monad, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: CompetitorUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad](
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
