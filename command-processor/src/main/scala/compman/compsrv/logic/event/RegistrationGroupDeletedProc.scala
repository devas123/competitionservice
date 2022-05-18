package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}

object RegistrationGroupDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: RegistrationGroupDeletedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      regInfo    <- state.registrationInfo
      regPeriods <- Option(regInfo.registrationPeriods)
      regGroups  <- Option(regInfo.registrationGroups)
      newPeriods = regPeriods.map { case (id, p) =>
        val regGrIds = Option(p.registrationGroupIds).getOrElse(Seq.empty)
        (id, p.withRegistrationGroupIds(regGrIds.filter(_ != payload.groupId)))
      }
      newGroups = regGroups - payload.groupId
      newState = state.copy(registrationInfo =
        Some(regInfo.withRegistrationGroups(newGroups).withRegistrationPeriods(newPeriods))
      )
    } yield newState
    Monad[F].pure(eventT)
  }
}
