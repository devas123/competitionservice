package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.event.Events
import compman.compsrv.query.service.repository.{
  CompetitionQueryOperations,
  CompetitionUpdateOperations,
  FightQueryOperations,
  FightUpdateOperations
}
import compman.compsrv.query.service.repository.AcademyOperations.AcademyService
import compman.compsrv.query.service.repository.EventOffsetOperations.EventOffsetService

object EventProcessors {
  def applyStatelessEvent[F[+_]: CompetitionLogging.Service: Monad: AcademyService](event: Events.Event[Any]): F[Unit] =
    List(AcademyAddedProc(), AcademyRemovedProc(), AcademyUpdatedProc()).reduce((a, b) => a.orElse(b)).apply(event)
  def applyEvent[F[
    +_
  ]: CompetitionLogging.Service: Monad: CompetitionQueryOperations: CompetitionUpdateOperations: FightQueryOperations: FightUpdateOperations](
    event: Events.Event[Any]
  ): F[Unit] = List(
    FightOrderChangedProc(),
    CategoryDeletedProc(),
    FightResultSetProc(),
    CompetitionDeletedProc(),
    CompetitorCategoryChangesProc(),
    BracketsDroppedProc(),
    ScheduleDroppedProc(),
    CategoryRegistrationStatusChangedProc(),
    BracketsGeneratedProc(),
    CategoryAddedProc(),
    CompetitionCreatedProc(),
    CompetitionPropertiesUpdatedProc(),
    CompetitionStatusUpdatedProc(),
    CompetitorAddedProc(),
    CompetitorRemovedProc(),
    CompetitorsPropagatedToStageProc(),
    CompetitorUpdatedProc(),
    FightCompetitorsAssignedProc(),
    FightEditorChangesAppliedProc(),
    FightsAddedToStageProc(),
    FightStartTimeUpdatedProc(),
    MatsUpdatedProc(),
    RegistrationGroupAddedProc(),
    RegistrationGroupCategoriesAssignedProc(),
    RegistrationGroupDeletedProc(),
    RegistrationInfoUpdatedProc(),
    RegistrationPeriodAddedProc(),
    RegistrationPeriodDeletedProc(),
    ScheduleGeneratedProc(),
    StageResultSetProc(),
    StageStatusUpdatedProc()
  ).reduce((a, b) => a.orElse(b)).apply(event)
}
