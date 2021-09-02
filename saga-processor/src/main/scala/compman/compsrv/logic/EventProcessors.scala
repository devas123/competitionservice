package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.Mapping.EventMapping
import compman.compsrv.logic.event._
import compman.compsrv.model.event.Events
import compman.compsrv.model.{CompetitionState, Payload}

object EventProcessors {
  import Operations._

  def applyEvent[F[+_]: Monad: IdOperations: EventOperations: EventMapping, P <: Payload](
    event: Events.Event[P],
    state: CompetitionState
  ): F[CompetitionState] = List(
    CategoryRegistrationStatusChangedProc(state),
    BracketsGeneratedProc(state),
    CategoryAddedProc(state),
    CompetitionCategoriesProc(state),
    CompetitionCreatedProc(state),
    CompetitionPropertiesUpdatedProc(state),
    CompetitionStatusUpdatedProc(state),
    CompetitorAddedProc(state),
    CompetitorRemovedProc(state),
    CompetitorsPropagatedToStageProc(state),
    CompetitorUpdatedProc(state),
    DashboardCreatedProc(state),
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
    StageStatusUpdatedProc(state),
  ).reduce((a, b) => a.orElse(b)).apply(event)
}
