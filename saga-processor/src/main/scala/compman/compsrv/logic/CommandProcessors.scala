package compman.compsrv.logic

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic
import compman.compsrv.logic.command._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{CategoryRegistrationStatusChangeCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError

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
//      FightEditorApplyChangesProc(state),
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