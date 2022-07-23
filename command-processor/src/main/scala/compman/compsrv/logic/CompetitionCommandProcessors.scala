package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command._
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.InternalCommandProcessorCommand
import compservice.model.protobuf.event.Event
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object CompetitionCommandProcessors {
  import Operations._

  def process[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: InternalCommandProcessorCommand[Any],
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    Seq(
      PublishCompetitionProc(state),
      UnpublishCompetitionProc(state),
      DeleteCategoryProc(state),
      DeleteCompetitionProc(),
      UpdateCompetitionPropertiesProc(),
      DropBracketsProc(state),
      DropScheduleProc(),
      AddCategoryProc(state),
      AddCompetitorProc(state),
      CreateFakeCompetitorsProc(),
      CategoryRegistrationStatusChangeProc(state),
      ChangeCompetitorCategoryProc(state),
      ChangeFightOrderProc(state),
      CreateCompetitionProc(),
      FightEditorApplyChangesProc(state),
      GenerateAbsoluteCategoryProc(state),
      GenerateBracketsProc(state),
      GenerateCategoriesFromRestrictionsProc(state),
      GenerateScheduleProc(state),
      PropagateCompetitorsProc(state),
      RemoveCompetitorProc(state),
      SetFightResultProc(state),
      UpdateCompetitorProc(state),
      UpdateRegistrationInfoProc(),
      UpdateStageStatusProc(state)
    ).reduce((a, b) => a.orElse(b)).apply(command)
  }
}
