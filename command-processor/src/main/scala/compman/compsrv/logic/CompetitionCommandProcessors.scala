package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command._
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.InternalCommandProcessorCommand
import compservice.model.protobuf.event.Event

object CompetitionCommandProcessors {
  import Operations._

  def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations: Interpreter, P](
                                                                                                                   command: InternalCommandProcessorCommand[Any],
                                                                                                                   state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    Seq(
      DeleteCategoryProc(),
      DeleteCompetitionProc(),
      UpdateCompetitionPropertiesProc(),
      DropBracketsProc(state),
      DropScheduleProc(),
      AddCategoryProc(state),
      AddCompetitorProc(state),
      CreateFakeCompetitorsProc(),
      AddRegistrationGroupProc(state),
      AssignRegistrationGroupCategoriesProc(state),
      CategoryRegistrationStatusChangeProc(state),
      AddRegistrationPeriodProc(state),
      ChangeCompetitorCategoryProc(state),
      ChangeFightOrderProc(state),
      CreateCompetitionProc(),
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
      UpdateRegistrationInfoProc(),
      UpdateStageStatusProc(state)
    ).reduce((a, b) => a.orElse(b)).apply(command)
  }
}
