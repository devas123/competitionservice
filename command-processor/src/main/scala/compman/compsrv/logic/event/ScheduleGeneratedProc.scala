package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleDTO}
import compman.compsrv.model.event.Events.{Event, ScheduleGeneratedEvent}

object ScheduleGeneratedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: ScheduleGeneratedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: ScheduleGeneratedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      id      <- event.competitionId
      sc      <- Option(payload.getSchedule)
      mats    <- Option(sc.getMats).orElse(Some(Array.empty[MatDescriptionDTO]))
      periods <- Option(sc.getPeriods).orElse(Some(Array.empty[PeriodDTO]))
      schedule = new ScheduleDTO().setId(id).setMats(mats).setPeriods(periods)
      newState = state.copy(schedule = Some(schedule))
    } yield newState
    Monad[F].pure(eventT)
  }
}
