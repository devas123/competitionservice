package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, ScheduleDropped}

object ScheduleDroppedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: ScheduleDropped => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: ScheduleDropped,
    state: CompetitionState
  ): F[Option[CompetitionState]] = { Monad[F].pure(Some(state.createCopy(schedule = None))) }
}
