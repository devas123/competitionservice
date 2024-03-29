package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.Utils.groupById
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, MatsUpdatedEvent}

object MatsUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: MatsUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: MatsUpdatedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload  <- event.payload
      schedule <- state.schedule
      mats     <- Option(schedule.mats).orElse(Some(Seq.empty))
      updates  <- Option(payload.mats).map(ms => groupById(ms)(_.id))
      updatedMats = mats.map(mat => {
        updates.get(mat.id).map(m => {
          mat.withName(Option(m.name).getOrElse(mat.name))
            .withMatOrder(Option(m.matOrder).getOrElse(mat.matOrder))
            .withPeriodId(Option(m.periodId).getOrElse(mat.periodId))
        }).getOrElse(mat)
      })
      newSchedule = schedule.withMats(updatedMats)
      newState    = state.copy(schedule = Option(newSchedule))
    } yield newState
    Monad[F].pure(eventT)
  }
}
