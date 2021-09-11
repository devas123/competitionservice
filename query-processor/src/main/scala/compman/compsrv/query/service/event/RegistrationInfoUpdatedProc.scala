package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, RegistrationInfoUpdatedEvent}

object RegistrationInfoUpdatedProc {
  def apply[F[+_]: Monad, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationInfoUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad](
    event: RegistrationInfoUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      newRegInfo <- Option(payload.getRegistrationInfo)
      regInfo    <- state.registrationInfo
      update = regInfo.setRegistrationOpen(newRegInfo.getRegistrationOpen)
        .setRegistrationPeriods(newRegInfo.getRegistrationPeriods)
        .setRegistrationGroups(newRegInfo.getRegistrationGroups)
      newState = state.createCopy(registrationInfo = Some(update))
    } yield newState
    Monad[F].pure(eventT)
  }
}
