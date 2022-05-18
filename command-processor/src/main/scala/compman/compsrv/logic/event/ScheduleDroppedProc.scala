package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, ScheduleDropped}

object ScheduleDroppedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case _: ScheduleDropped => dropSchedule[F](state) }

  private def dropSchedule[F[+_]: Monad: IdOperations: EventOperations](
                                                                  state: CompetitionState
  ): F[Option[CompetitionState]] = { Monad[F].pure(Some(state.copy(schedule = None))) }
}
