package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitionCategoriesAddedEvent, CompetitorAddedEvent, Event}
import compman.compsrv.model.{CompetitionState, Payload}

object CompetitionCategoriesProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[CompetitionState]] = {
    case x: CompetitionCategoriesAddedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitionCategoriesAddedEvent,
                                                                     state: CompetitionState
                                                                   ): F[CompetitionState] = {
    val eventT = for {
      payload <- event.payload
      newState = state.createCopy()
    } yield newState
    Monad[F].pure(eventT.getOrElse(state))
  }
}
