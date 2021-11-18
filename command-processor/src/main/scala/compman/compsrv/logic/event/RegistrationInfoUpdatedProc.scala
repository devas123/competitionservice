package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, RegistrationInfoUpdatedEvent}

object RegistrationInfoUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationInfoUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
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
      newState = state.copy(registrationInfo = Some(update))
    } yield newState
    Monad[F].pure(eventT)
  }
}
