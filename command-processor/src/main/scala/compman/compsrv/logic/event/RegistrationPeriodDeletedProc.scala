package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationPeriodDeletedEvent}

object RegistrationPeriodDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: RegistrationPeriodDeletedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationPeriodDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      deleted <- Option(payload.periodId)
      regInfo <- state.registrationInfo
      periods <- Option(regInfo.registrationPeriods)
      newPeriods = periods - deleted
      newState   = state.copy(registrationInfo = Some(regInfo.withRegistrationPeriods(newPeriods)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
