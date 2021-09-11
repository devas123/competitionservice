package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, FightsAddedToStageEvent}

object FightsAddedToStageProc {
  def apply[F[+_]: Monad, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightsAddedToStageEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad](
    event: FightsAddedToStageEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import compman.compsrv.model.extensions._
    val eventT = for {
      payload   <- event.payload
      newfights <- Option(payload.getFights)
      newState = state.updateFights(newfights.toIndexedSeq)
    } yield newState
    Monad[F].pure(eventT)
  }
}
