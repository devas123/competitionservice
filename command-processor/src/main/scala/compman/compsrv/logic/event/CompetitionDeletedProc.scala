package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.event.Events.{CompetitionDeletedEvent, Event}

object CompetitionDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: CompetitionDeletedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitionDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = { Monad[F].pure(Option(state.updateStatus(CompetitionStatus.DELETED))) }
}
