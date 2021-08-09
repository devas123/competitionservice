package compman.compsrv.logic

import cats.Monad
import cats.data.EitherT
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{AddCategory, AddCompetitorCommand, Command}
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.Errors._
import compman.compsrv.model.command.Commands

object CommandProcessors {
  import Operations._

  def process[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      command: Command[P],
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] =
    command match {
      case AddCategory(payload, competitionId) => ???
      case x @ AddCompetitorCommand(_, _, _) =>
        addCompetitor(x, state)
      case Commands.AddRegistrationGroupCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.AddRegistrationPeriodCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.AssignRegistrationGroupCategoriesCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.CategoryRegistrationStatusChangeCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.ChangeCompetitorCategoryCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.ChangeFightOrderCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.CreateCompetitionCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.DeleteRegistrationGroupCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.DeleteRegistrationPeriodCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.FightEditorApplyChangesCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.GenerateAbsoluteCategoryCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.GenerateBracketsCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.GenerateCategoriesFromRestrictionsCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.GenerateScheduleCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.PropagateCompetitorsCommand(payload, competitionId, categoryId) =>
        ???
      case Commands
            .RegistrationPeriodAddRegistrationGroupCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.RemoveCompetitorCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.SetFightResultCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.UpdateCompetitorCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.UpdateRegistrationInfoCommand(payload, competitionId, categoryId) =>
        ???
      case Commands.UpdateStageStatusCommand(payload, competitionId, categoryId) =>
        ???
    }

  def addCompetitor[F[+_]: Monad: IdOperations: EventOperations](
      command: AddCompetitorCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, GeneralError())
        id <- EitherT
          .liftF[F, Errors.Error, String](IdOperations[F].createId(payload.getCompetitor))
        exists <- EitherT
          .fromOption(state.competitors.map(_.exists(_.getId == id)), Errors.GeneralError())
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.COMPETITOR_ADDED,
                competitorId = Some(id),
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(new CompetitorAddedPayload(payload.getCompetitor))
              )
            )
          } else {
            EitherT(
              CommandEventOperations[F, EventDTO, EventType]
                .error(CompetitorAlreadyExists(id, payload.getCompetitor))
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
