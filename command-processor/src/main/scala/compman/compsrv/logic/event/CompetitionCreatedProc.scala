package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitionCreatedEvent, Event}

object CompetitionCreatedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CommandProcessorCompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: CompetitionCreatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitionCreatedEvent,
                                                                     state: CommandProcessorCompetitionState
                                                                   ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      props = payload.getProperties
      regInfo = payload.getReginfo
      newState = state.copy(competitionProperties = Some(props), registrationInfo = Some(regInfo))
    } yield newState
    Monad[F].pure(eventT)
  }
}
