package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command._
import compman.compsrv.model.command.Commands.Command
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.{CompetitionState, Errors, Payload}

object CommandProcessors {
  import Operations._

  def process[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      command: Command[P],
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    List(
      AddCategoryProc(state),
      AddCompetitorProc(state),
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
      UpdateStageStatusProc(state))
      .reduce((a, b) => a.orElse(b)).apply(command)
  }
}