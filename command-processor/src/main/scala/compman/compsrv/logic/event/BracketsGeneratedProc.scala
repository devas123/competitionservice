package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.schedule.StageGraph
import compman.compsrv.model.event.Events.{BracketsGeneratedEvent, Event}
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, DiGraph}

object BracketsGeneratedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: BracketsGeneratedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: BracketsGeneratedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      categoryId <- event.categoryId
      categoryFightsIndex <- payload.categoryFightsIndex
      newState = state
        .addAllStages(payload.stages.map(s => s.id -> s))
        .addCategoryIdToFightsIndex((categoryId, categoryFightsIndex))
        .withStageGraph(StageGraph.mergeDigraphs(
          state.stageGraph.getOrElse(DiGraph()),
          payload.stageGraph.getOrElse(DiGraph())
        ))
    } yield newState
    Monad[F].pure(eventT)
  }
}
