package compman.compsrv.logic.event

import cats.Monad
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, ScheduleDropped}

object ScheduleDroppedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case _: ScheduleDropped =>
    dropSchedule[F](state)
  }

  private def dropSchedule[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = { Monad[F].pure(Some(state.copy(schedule = None))) }
}
