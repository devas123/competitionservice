package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.RegistrationPeriodDTO
import compman.compsrv.model.event.Events.{Event, RegistrationPeriodDeletedEvent}

object RegistrationPeriodDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationPeriodDeletedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationPeriodDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      deleted <- Option(payload.getPeriodId)
      regInfo <- state.registrationInfo
      periods <- Option(regInfo.getRegistrationPeriods).orElse(Some(Array.empty[RegistrationPeriodDTO]))
      newPeriods = periods.filter(_.getId != deleted)
      newState   = state.copy(registrationInfo = Some(regInfo.setRegistrationPeriods(newPeriods)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
