package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, StageStatusUpdatedEvent}

object StageStatusUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: StageStatusUpdatedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: StageStatusUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      status  <- Option(payload.status)
      stages  <- state.stages
      stage   <- stages.get(payload.stageId)
      newState = state.updateStage(stage.withStageStatus(status))
    } yield newState
    Monad[F].pure(eventT)
  }
}
