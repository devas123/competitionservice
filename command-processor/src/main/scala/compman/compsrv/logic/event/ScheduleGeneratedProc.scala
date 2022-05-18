package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, ScheduleGeneratedEvent}
import compservice.model.protobuf.model.Schedule

object ScheduleGeneratedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: ScheduleGeneratedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: ScheduleGeneratedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      id      <- event.competitionId
      sc      <- Option(payload.getSchedule)
      mats    <- Option(sc.mats).orElse(Some(Seq.empty))
      periods <- Option(sc.periods).orElse(Some(Seq.empty))
      schedule = Schedule().withId(id).withMats(mats).withPeriods(periods)
      newState = state.copy(schedule = Some(schedule))
    } yield newState
    Monad[F].pure(eventT)
  }
}
