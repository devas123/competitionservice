package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupCategoriesAssignedEvent}

object RegistrationGroupCategoriesAssignedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: RegistrationGroupCategoriesAssignedEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupCategoriesAssignedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      regInfo     <- state.registrationInfo
      groups      <- Option(regInfo.registrationGroups)
      targetGroup <- groups.get(payload.groupId)
      updatedGroup  = targetGroup.withCategories(payload.categories)
      updatedGroups = groups + (payload.groupId -> updatedGroup)
      newState      = state.copy(registrationInfo = Some(regInfo.withRegistrationGroups(updatedGroups)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
