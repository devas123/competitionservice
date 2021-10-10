package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupAddedEvent}

object RegistrationGroupAddedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationGroupAddedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupAddedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload             <- event.payload
      regInfo             <- state.registrationInfo
      regPeriods          <- Option(regInfo.getRegistrationPeriods)
      regPeriod           <- regPeriods.find(_.getId == payload.getPeriodId)
      addedGroups         <- Option(payload.getGroups)
      currentPeriodGroups <- Option(regPeriod.getRegistrationGroupIds).orElse(Some(Array.empty))
      ind = regPeriods.indexOf(regPeriod)
      regGroups <- Option(regInfo.getRegistrationGroups).orElse(Some(Array.empty))
      newPeriods =
        (regPeriods.slice(0, ind) :+
          regPeriod.setRegistrationGroupIds((currentPeriodGroups ++ addedGroups.map(_.getId)).distinct)) ++
          regPeriods.slice(ind + 1, regPeriods.length)
      newGroups  = (regGroups ++ addedGroups).distinctBy(_.getId)
      newRegInfo = regInfo.setRegistrationPeriods(newPeriods).setRegistrationGroups(newGroups)
      newState   = state.createCopy(registrationInfo = Some(newRegInfo))
    } yield newState
    Monad[F].pure(eventT)
  }
}
