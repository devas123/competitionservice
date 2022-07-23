package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.event._
import compman.compsrv.model.event.Events
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object EventProcessors {
  import Operations._

  def applyEvent[F[+_]: Monad: IdOperations: EventOperations](
    event: Events.Event[Any],
    state: CommandProcessorCompetitionState
  ): F[CommandProcessorCompetitionState] = Monad[F].map(
    List(
      CategoryDeletedProc(state),
      FightResultSetProc(state),
      CompetitionDeletedProc(state),
      BracketsDroppedProc(state),
      ScheduleDroppedProc(state),
      CategoryRegistrationStatusChangedProc(state),
      CompetitorCategoryAddedProc(state),
      CompetitorCategoryChangedProc(state),
      BracketsGeneratedProc(state),
      CategoryAddedProc(state),
      CompetitionCreatedProc(state),
      CompetitionPropertiesUpdatedProc(state),
      CompetitionStatusUpdatedProc(state),
      CompetitorAddedProc(state),
      CompetitorRemovedProc(state),
      CompetitorsPropagatedToStageProc(state),
      CompetitorUpdatedProc(state),
      FightCompetitorsAssignedProc(state),
      FightEditorChangesAppliedProc(state),
      FightsAddedToStageProc(state),
      FightStartTimeUpdatedProc(state),
      MatsUpdatedProc(state),
      RegistrationInfoUpdatedProc(state),
      ScheduleGeneratedProc(state),
      StageResultSetProc(state),
      StageStatusUpdatedProc(state)
    ).reduce((a, b) => a.orElse(b)).apply(event)
  )(_.map(s => s.copy(revision = s.revision + 1)).getOrElse(state))
}
