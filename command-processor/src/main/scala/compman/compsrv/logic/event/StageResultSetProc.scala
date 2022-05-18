package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, StageResultSetEvent}
import compservice.model.protobuf.model.StageStatus

object StageResultSetProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: StageResultSetEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: StageResultSetEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload          <- event.payload
      stages           <- state.stages
      stage            <- stages.get(payload.stageId)
      resultDescriptor <- Option(stage.getStageResultDescriptor)
      newStage = stage.withStageStatus(StageStatus.FINISHED)
        .withStageResultDescriptor(resultDescriptor.withCompetitorResults(payload.results))
      newState = state.updateStage(newStage)
    } yield newState
    Monad[F].pure(eventT)
  }
}
