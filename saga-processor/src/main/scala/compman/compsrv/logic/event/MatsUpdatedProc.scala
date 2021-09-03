package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, MatsUpdatedEvent}

object MatsUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: MatsUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: MatsUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload  <- event.payload
      schedule <- state.schedule
      mats     <- Option(schedule.getMats).orElse(Some(Array.empty))
      updates  <- Option(payload.getMats).map(_.groupMapReduce(_.getId)(identity)((a, _) => a))
      updatedMats = mats.map(mat => {
        updates.get(mat.getId).map(m => {
          mat.setName(Option(m.getName).getOrElse(mat.getName))
            .setMatOrder(Option(m.getMatOrder).getOrElse(mat.getMatOrder))
            .setPeriodId(Option(m.getPeriodId).getOrElse(mat.getPeriodId))
        }).getOrElse(mat)
      })
      newSchedule = schedule.setMats(updatedMats)
      newState    = state.createCopy(schedule = Option(newSchedule))
    } yield newState
    Monad[F].pure(eventT)
  }
}
