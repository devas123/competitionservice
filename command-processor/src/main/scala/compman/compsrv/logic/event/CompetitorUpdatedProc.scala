package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorUpdatedEvent, Event}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object CompetitorUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: CompetitorUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorUpdatedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      competitor <- payload.competitor
      competitors = state.competitors
      newState    = state.copy(competitors = competitors + (competitor.id -> competitor))
    } yield newState
    Monad[F].pure(eventT)
  }
}
