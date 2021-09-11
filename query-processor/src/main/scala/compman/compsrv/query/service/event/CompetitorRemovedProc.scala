package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitorRemovedEvent, Event}

object CompetitorRemovedProc {
  def apply[F[+_] : Monad, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitorRemovedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad](
                                                                     event: CompetitorRemovedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      comps <- state.competitors
      newState = state.createCopy(
        competitors = Some(comps - payload.getFighterId))
    } yield newState
    Monad[F].pure(eventT)
  }
}
