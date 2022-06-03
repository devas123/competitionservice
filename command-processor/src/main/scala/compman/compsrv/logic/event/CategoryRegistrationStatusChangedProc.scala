package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryRegistrationStatusChanged, Event}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object CategoryRegistrationStatusChangedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CommandProcessorCompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: CategoryRegistrationStatusChanged =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CategoryRegistrationStatusChanged,
                                                                     state: CommandProcessorCompetitionState
                                                                   ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      comProps <- state.registrationInfo
      newState = state.copy(registrationInfo = Some(comProps.withRegistrationOpen(payload.newStatus)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
