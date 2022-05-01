package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, RegistrationGroupCategoriesAssignedEvent}

import scala.jdk.CollectionConverters._

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
      targetGroup <- groups.asScala.get(payload.getGroupId)
      updatedGroup  = targetGroup.setCategories(payload.getCategories)
      updatedGroups = groups.asScala.toMap + (payload.getGroupId -> updatedGroup)
      newState      = state.copy(registrationInfo = Some(regInfo.setRegistrationGroups(updatedGroups.asJava)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
