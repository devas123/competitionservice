package compman.compsrv.logic

import cats.Monad
import compman.compsrv.logic.command._
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.Command
import compman.compsrv.model.events.EventDTO

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
      AddRegistrationPeriodProc(state)
    ).reduce((a, b) => a.orElse(b)).apply(command)
/*
    command match {
      case x @ Commands.ChangeCompetitorCategoryCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.ChangeFightOrderCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.CreateCompetitionCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.DeleteRegistrationGroupCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.DeleteRegistrationPeriodCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.FightEditorApplyChangesCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.GenerateAbsoluteCategoryCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.GenerateBracketsCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands
            .GenerateCategoriesFromRestrictionsCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.GenerateScheduleCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.PropagateCompetitorsCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands
            .RegistrationPeriodAddRegistrationGroupCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.RemoveCompetitorCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.SetFightResultCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.UpdateCompetitorCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.UpdateRegistrationInfoCommand(payload, competitionId, categoryId) =>
        ???
      case x @ Commands.UpdateStageStatusCommand(payload, competitionId, categoryId) =>
        ???
    }
*/
  }
}
