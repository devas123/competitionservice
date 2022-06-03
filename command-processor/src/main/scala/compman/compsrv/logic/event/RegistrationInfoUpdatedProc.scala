package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationInfoUpdatedEvent}

object RegistrationInfoUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: RegistrationInfoUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationInfoUpdatedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      newRegInfo <- Option(payload.getRegistrationInfo)
      regInfo    <- state.registrationInfo
      update = regInfo.withRegistrationOpen(newRegInfo.registrationOpen)
        .withRegistrationPeriods(newRegInfo.registrationPeriods)
        .withRegistrationGroups(newRegInfo.registrationGroups)
      newState = state.copy(registrationInfo = Some(update))
    } yield newState
    Monad[F].pure(eventT)
  }
}
