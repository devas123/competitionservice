package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.command.Commands.{
  AssignRegistrationGroupCategoriesCommand,
  InternalCommandProcessorCommand
}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.RegistrationGroupCategoriesAssignedPayload

object AssignRegistrationGroupCategoriesProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ AssignRegistrationGroupCategoriesCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: AssignRegistrationGroupCategoriesCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      allCategoriesExist = payload.categories.forall(b => state.categories.exists(_.contains(b)))
      groupExists        = state.registrationInfo.exists(_.registrationGroups.contains(payload.groupId))
      periodExists       = state.registrationInfo.exists(_.registrationPeriods.contains(payload.periodId))
      event <-
        if (!allCategoriesExist) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.CategoryDoesNotExist(payload.categories)))
        } else if (!groupExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.RegistrationGroupDoesNotExist(payload.groupId)))
        } else if (!periodExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.RegistrationPeriodDoesNotExist(payload.periodId)))
        } else {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = None,
            payload = Some(MessageInfo.Payload.RegistrationGroupCategoriesAssignedPayload(
              RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId, payload.categories)
            ))
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
