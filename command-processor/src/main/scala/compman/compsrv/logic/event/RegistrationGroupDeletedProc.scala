package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}

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
      newPeriods = regPeriods.map { case (id, p) =>
        val regGrIds = Option(p.registrationGroupIds).getOrElse(Seq.empty)
        (id, p.withRegistrationGroupIds(regGrIds.filter(_ != payload.groupId)))
      }
      newGroups = regGroups - payload.groupId
      newState = state
        .copy(registrationInfo = Some(regInfo.withRegistrationGroups(newGroups).withRegistrationPeriods(newPeriods)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
