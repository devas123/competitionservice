package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorAddedEvent, Event}

object CompetitorAddedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = {
    case x: CompetitorAddedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitorAddedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      competitor <- payload.competitor
      newState = state.copy(competitors = state.competitors.map(_ + (competitor.id -> competitor)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
