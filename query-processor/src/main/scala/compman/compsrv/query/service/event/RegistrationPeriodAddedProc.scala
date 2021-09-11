package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, RegistrationPeriodAddedEvent}

object RegistrationPeriodAddedProc {
  def apply[F[+_]: Monad, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationPeriodAddedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad](
    event: RegistrationPeriodAddedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      newP       <- Option(payload.getPeriod)
      regInfo    <- state.registrationInfo
      regPeriods <- Option(regInfo.getRegistrationPeriods).orElse(Some(Array.empty))
      newPeriods = (regPeriods :+ newP).distinctBy(_.getId)
      newState   = state.createCopy(registrationInfo = Some(regInfo.setRegistrationPeriods(newPeriods)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
