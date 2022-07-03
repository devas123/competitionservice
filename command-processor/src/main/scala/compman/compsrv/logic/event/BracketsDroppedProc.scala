package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.schedule.StageGraph
import compman.compsrv.model.event.Events.{CategoryBracketsDropped, Event}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object BracketsDroppedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: CategoryBracketsDropped =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CategoryBracketsDropped,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val maybeState = for {
      catId      <- event.categoryId
      stageGraph <- state.stageGraph
      stageIdsToRemove = state.stages.filter { case (_, o) => o.categoryId == catId }.keySet
      newStageGraph = stageIdsToRemove.foldLeft(stageGraph) { (graph, stageId) =>
        StageGraph.removeStage(graph, stageId)
      }
      newState = state.withStages(state.stages -- stageIdsToRemove)
        .fightsApply(fightsOpt => fightsOpt.map(_.filter(_._2.categoryId != catId))).withStageGraph(newStageGraph)
        .withCategoryIdToFightsIndex(state.categoryIdToFightsIndex - catId)
    } yield newState
    Monad[F].pure(maybeState)
  }
}
