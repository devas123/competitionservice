package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryRegistrationStatusChanged, CompetitorAddedEvent, Event}
import compman.compsrv.model.{CompetitionState, Payload}

object CategoryRegistrationStatusChangedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[CompetitionState]] = {
    case x: CategoryRegistrationStatusChanged =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CategoryRegistrationStatusChanged,
                                                                     state: CompetitionState
                                                                   ): F[CompetitionState] = {
    val eventT = for {
      payload <- event.payload
      comProps <- state.registrationInfo
      newState = state.createCopy(registrationInfo = Some(comProps.setRegistrationOpen(payload.isNewStatus)))
    } yield newState
    Monad[F].pure(eventT.getOrElse(state))
  }
}
