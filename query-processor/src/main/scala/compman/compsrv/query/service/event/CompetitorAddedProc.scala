package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitorAddedEvent, Event}

object CompetitorAddedProc {
  def apply[F[+_] : Monad, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitorAddedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad](
                                                                     event: CompetitorAddedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      newState = state.createCopy(competitors = state.competitors.map(_ + (payload.getFighter.getId -> payload.getFighter)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
