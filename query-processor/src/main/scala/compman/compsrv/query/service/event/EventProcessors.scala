package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events
import compman.compsrv.query.service.repository.CompetitionUpdateOperations

object EventProcessors {
  def applyEvent[F[+_] : CompetitionLogging.Service : Monad : CompetitionUpdateOperations, P <: Payload](
                                                                                                          event: Events.Event[P]
                                                                                                        ): F[Unit] = List(
    CategoryRegistrationStatusChangedProc(),
    BracketsGeneratedProc(),
    //      CategoryAddedProc(),
    //      CompetitionCreatedProc(),
    //      CompetitionPropertiesUpdatedProc(),
    //      CompetitionStatusUpdatedProc(),
    //      CompetitorAddedProc(),
    //      CompetitorRemovedProc(),
    //      CompetitorsPropagatedToStageProc(),
    //      CompetitorUpdatedProc(),
    //      FightCompetitorsAssignedProc(),
    //      FightEditorChangesAppliedProc(),
    //      FightPropertiesUpdatedProc(),
    //      FightsAddedToStageProc(),
    //      FightStartTimeUpdatedProc(),
    //      MatsUpdatedProc(),
    //      RegistrationGroupAddedProc(),
    //      RegistrationGroupCategoriesAssignedProc(),
    //      RegistrationGroupDeletedProc(),
    //      RegistrationInfoUpdatedProc(),
    //      RegistrationPeriodAddedProc(),
    //      RegistrationPeriodDeletedProc(),
    //      ScheduleGeneratedProc(),
    //      StageResultSetProc(),
    //      StageStatusUpdatedProc()
  ).reduce((a, b) => a.orElse(b)).apply(event)
}
