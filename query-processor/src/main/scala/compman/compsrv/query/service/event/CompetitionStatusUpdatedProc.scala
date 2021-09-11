package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitionStatusUpdatedEvent, Event}

object CompetitionStatusUpdatedProc {
  def apply[F[+_] : Monad, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitionStatusUpdatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad](
                                                                     event: CompetitionStatusUpdatedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      props <- state.competitionProperties
      newState = state.createCopy(competitionProperties = Some(props.setStatus(payload.getStatus)))} yield newState
    Monad[F].pure(eventT)
  }
}
