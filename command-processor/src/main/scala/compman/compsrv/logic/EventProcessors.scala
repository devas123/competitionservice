package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.event._
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events

object EventProcessors {
  import Operations._

  def applyEvent[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations, P <: Payload](
    event: Events.Event[P],
    state: CompetitionState
  ): F[CompetitionState] = Monad[F].map(
    List(
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
      FightPropertiesUpdatedProc(state),
      FightsAddedToStageProc(state),
      FightStartTimeUpdatedProc(state),
      MatsUpdatedProc(state),
      RegistrationGroupAddedProc(state),
      RegistrationGroupCategoriesAssignedProc(state),
      RegistrationGroupDeletedProc(state),
      RegistrationInfoUpdatedProc(state),
      RegistrationPeriodAddedProc(state),
      RegistrationPeriodDeletedProc(state),
      ScheduleGeneratedProc(state),
      StageResultSetProc(state),
      StageStatusUpdatedProc(state)
    ).reduce((a, b) => a.orElse(b)).apply(event)
  )(_.map(s => s.copy(revision = s.revision + 1)).getOrElse(state))
}
