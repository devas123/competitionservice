package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.command.Commands.{AssignRegistrationGroupCategoriesCommand, Command}
import compman.compsrv.model.events.payload.RegistrationGroupCategoriesAssignedPayload

object AssignRegistrationGroupCategoriesProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ AssignRegistrationGroupCategoriesCommand(_, _, _) =>
      process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: AssignRegistrationGroupCategoriesCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        allCategoriesExist = payload.getCategories.forall(b => state.categories.exists(_.exists(_.getId == b)))
        groupExists = state.registrationInfo.exists(_.getRegistrationGroups.exists(_.getId == payload.getGroupId))
        periodExists = state.registrationInfo.exists(_.getRegistrationPeriods.exists(_.getId == payload.getPeriodId))
        event <- if (!allCategoriesExist) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.CategoryDoesNotExist(payload.getCategories)))
        } else if (!groupExists) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.RegistrationGroupDoesNotExist(payload.getGroupId)))
        } else if (!periodExists) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.RegistrationPeriodDoesNotExist(payload.getPeriodId)))
        } else {
          EitherT.liftF[F, Errors.Error, EventDTO](
            CommandEventOperations[F, EventDTO, EventType].create(
              `type` = EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED,
              competitorId = None,
              competitionId = command.competitionId,
              categoryId = None,
              payload = Some(
                new RegistrationGroupCategoriesAssignedPayload(
                  payload.getPeriodId,
                  payload.getGroupId,
                  payload.getCategories
                )
              )
            )
          )
        }
      } yield Seq(event)
    eventT.value
  }
}
