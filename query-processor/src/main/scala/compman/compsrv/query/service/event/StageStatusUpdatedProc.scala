package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, StageStatusUpdatedEvent}

object StageStatusUpdatedProc {
  def apply[F[+_]: Monad, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: StageStatusUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad](
    event: StageStatusUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import compman.compsrv.model.extensions._
    val eventT = for {
      payload <- event.payload
      status  <- Option(payload.getStatus)
      stages  <- state.stages
      stage   <- stages.get(payload.getStageId)
      newState = state.updateStage(stage.setStageStatus(status))
    } yield newState
    Monad[F].pure(eventT)
  }
}
