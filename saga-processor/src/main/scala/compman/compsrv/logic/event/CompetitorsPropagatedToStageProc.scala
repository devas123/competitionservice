package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorAddedEvent, CompetitorsPropagatedToStageEvent, Event}
import compman.compsrv.model.{CompetitionState, Payload}

object CompetitorsPropagatedToStageProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitorsPropagatedToStageEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitorsPropagatedToStageEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload

      newState = state.createCopy()
    } yield newState
    Monad[F].pure(eventT)
  }
}
