package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.event.Events.{Event, StageResultSetEvent}

object StageResultSetProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: StageResultSetEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: StageResultSetEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import compman.compsrv.model.extensions._
    val eventT = for {
      payload          <- event.payload
      stages           <- state.stages
      stage            <- stages.get(payload.getStageId)
      resultDescriptor <- Option(stage.getStageResultDescriptor)
      newStage = stage.setStageStatus(StageStatus.FINISHED)
        .setStageResultDescriptor(resultDescriptor.setCompetitorResults(payload.getResults))
      newState = state.updateStage(newStage)
    } yield newState
    Monad[F].pure(eventT)
  }
}
