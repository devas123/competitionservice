package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryRegistrationStatusChanged, Event}

object CategoryRegistrationStatusChangedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = {
    case x: CategoryRegistrationStatusChanged =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CategoryRegistrationStatusChanged,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      comProps <- state.registrationInfo
      newState = state.copy(registrationInfo = Some(comProps.withRegistrationOpen(payload.newStatus)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
