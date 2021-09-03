package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupCategoriesAssignedEvent}

object RegistrationGroupCategoriesAssignedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationGroupCategoriesAssignedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupCategoriesAssignedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      regInfo     <- state.registrationInfo
      groups      <- Option(regInfo.getRegistrationGroups)
      targetGroup <- groups.find(_.getId == payload.getGroupId)
      ind           = groups.indexOf(targetGroup)
      updatedGroup  = targetGroup.setCategories(payload.getCategories)
      updatedGroups = (groups.slice(0, ind) :+ updatedGroup) ++ groups.slice(ind + 1, groups.length)
      newState      = state.createCopy(registrationInfo = Some(regInfo.setRegistrationGroups(updatedGroups)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
