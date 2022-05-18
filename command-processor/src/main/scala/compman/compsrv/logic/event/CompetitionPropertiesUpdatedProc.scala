package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitionPropertiesUpdatedEvent, Event}
import compman.compsrv.model.extensions._

object CompetitionPropertiesUpdatedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = {
    case x: CompetitionPropertiesUpdatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitionPropertiesUpdatedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      props = payload.getProperties
      stateProps <- state.competitionProperties
      newState = state.copy(competitionProperties = Some(stateProps.applyProperties(props)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
