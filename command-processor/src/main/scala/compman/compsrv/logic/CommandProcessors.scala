package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command._
import compman.compsrv.logic.fights.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.Command
import compman.compsrv.model.events.EventDTO

object CommandProcessors {
  import Operations._

  def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations: Interpreter, P <: Payload](
    command: Command[P],
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    Seq(
      UpdateCompetitionPropertiesProc(state),
      DropBracketsProc(state),
      DropScheduleProc(state),
      AddCategoryProc(state),
      AddCompetitorProc(state),
      CreateFakeCompetitorsProc(),
      AddRegistrationGroupProc(state),
      AssignRegistrationGroupCategoriesProc(state),
      CategoryRegistrationStatusChangeProc(state),
      AddRegistrationPeriodProc(state),
      ChangeCompetitorCategoryProc(state),
      ChangeFightOrderProc(state),
      CreateCompetitionProc(state),
      DeleteRegistrationGroupProc(state),
      DeleteRegistrationPeriodProc(state),
      FightEditorApplyChangesProc(state),
      GenerateAbsoluteCategoryProc(state),
      GenerateBracketsProc(state),
      GenerateCategoriesFromRestrictionsProc(state),
      GenerateScheduleProc(state),
      PropagateCompetitorsProc(state),
      RegistrationPeriodAddRegistrationGroupProc(state),
      RemoveCompetitorProc(state),
      SetFightResultProc(state),
      UpdateCompetitorProc(state),
      UpdateRegistrationInfoProc(state),
      UpdateStageStatusProc(state)
    ).reduce((a, b) => a.orElse(b)).apply(command)
  }
}
