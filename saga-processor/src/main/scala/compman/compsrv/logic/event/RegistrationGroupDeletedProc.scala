package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.dto.competition.{RegistrationGroupDTO, RegistrationPeriodDTO}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}

object RegistrationGroupDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationGroupDeletedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      regInfo    <- state.registrationInfo
      regPeriods <- Option(regInfo.getRegistrationPeriods).orElse(Some(Array.empty[RegistrationPeriodDTO]))
      regGroups  <- Option(regInfo.getRegistrationGroups).orElse(Some(Array.empty[RegistrationGroupDTO]))
      newPeriods = regPeriods.map(p => {
        val regGrIds = Option(p.getRegistrationGroupIds).getOrElse(Array.empty[String])
        p.setRegistrationGroupIds(regGrIds.filter(_ != payload.getGroupId))
      })
      newGroups = regGroups.filter(_.getId != payload.getGroupId)
      newState = state.createCopy(registrationInfo =
        Some(regInfo.setRegistrationGroups(newGroups).setRegistrationPeriods(newPeriods))
      )
    } yield newState
    Monad[F].pure(eventT)
  }
}
