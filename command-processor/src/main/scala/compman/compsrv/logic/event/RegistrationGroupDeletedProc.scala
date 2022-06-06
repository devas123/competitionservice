package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object RegistrationGroupDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: RegistrationGroupDeletedEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupDeletedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      regInfo    <- state.registrationInfo
      regPeriods <- Option(regInfo.registrationPeriods)
      regGroups  <- Option(regInfo.registrationGroups)
      period        = regPeriods(payload.periodId)
      updatedPeriod = period.update(_.registrationGroupIds := period.registrationGroupIds.filter(_ != payload.groupId))
      newPeriods    = regPeriods + (updatedPeriod.id -> updatedPeriod)
      shouldDeleteGroup = regPeriods.forall { case (_, p) => !p.registrationGroupIds.contains(payload.groupId) }
      newGroups         = if (shouldDeleteGroup) regGroups - payload.groupId else regGroups
      newState = state
        .copy(registrationInfo = Some(regInfo.withRegistrationGroups(newGroups).withRegistrationPeriods(newPeriods)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
