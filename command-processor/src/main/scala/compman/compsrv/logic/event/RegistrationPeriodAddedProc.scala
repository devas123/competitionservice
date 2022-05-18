package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationPeriodAddedEvent}

object RegistrationPeriodAddedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: RegistrationPeriodAddedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationPeriodAddedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      newP       <- Option(payload.getPeriod)
      regInfo    <- state.registrationInfo
      regPeriods <- Option(regInfo.registrationPeriods)
      newPeriods = regPeriods + (newP.id -> newP)
      newState   = state.copy(registrationInfo = Some(regInfo.withRegistrationPeriods(newPeriods)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
