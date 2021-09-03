package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, StageStatusUpdatedEvent}

object StageStatusUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: StageStatusUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: StageStatusUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import compman.compsrv.model.extension._
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
