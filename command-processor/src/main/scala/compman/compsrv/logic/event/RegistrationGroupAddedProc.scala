package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupAddedEvent}

object RegistrationGroupAddedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: RegistrationGroupAddedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupAddedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload             <- event.payload
      regInfo             <- state.registrationInfo
      regPeriods          <- Option(regInfo.registrationPeriods)
      regPeriod           <- regPeriods.get(payload.periodId)
      addedGroups         <- Option(payload.groups)
      currentPeriodGroups <- Option(regPeriod.registrationGroupIds).orElse(Some(Seq.empty[String]))
      regGroups <- Option(regInfo.registrationGroups).orElse(Some(Map.empty))
      updatedPeriod = regPeriod.withRegistrationGroupIds((currentPeriodGroups ++ addedGroups.map(_.id)).distinct)
      newPeriods = regPeriods + (payload.periodId -> updatedPeriod)
      newGroups  = regGroups ++ addedGroups.map(g => g.id -> g)
      newRegInfo = regInfo.withRegistrationPeriods(newPeriods).withRegistrationGroups(newGroups.toMap)
      newState   = state.copy(registrationInfo = Some(newRegInfo))
    } yield newState
    Monad[F].pure(eventT)
  }
}
