package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CategoryRegistrationStatusChanged, Event}

object CategoryRegistrationStatusChangedProc {
  def apply[F[+_] : Monad, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CategoryRegistrationStatusChanged =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad](
                                                                     event: CategoryRegistrationStatusChanged,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      comProps <- state.registrationInfo
      newState = state.createCopy(registrationInfo = Some(comProps.setRegistrationOpen(payload.isNewStatus)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
