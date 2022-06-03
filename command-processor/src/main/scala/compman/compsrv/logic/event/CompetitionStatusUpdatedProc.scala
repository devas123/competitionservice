package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitionStatusUpdatedEvent, Event}

object CompetitionStatusUpdatedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CommandProcessorCompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: CompetitionStatusUpdatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitionStatusUpdatedEvent,
                                                                     state: CommandProcessorCompetitionState
                                                                   ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      props <- state.competitionProperties
      newState = state.copy(competitionProperties = Some(props.withStatus(payload.status)))} yield newState
    Monad[F].pure(eventT)
  }
}
