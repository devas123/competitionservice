package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{DeleteRegistrationGroupCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.RegistrationGroupDeletedPayload

object DeleteRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ DeleteRegistrationGroupCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: DeleteRegistrationGroupCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      groupExists  = state.registrationInfo.exists(_.registrationGroups.contains(payload.groupId))
      periodExists = state.registrationInfo.exists(_.registrationPeriods.contains(payload.periodId))
      event <-
        if (!groupExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.RegistrationGroupDoesNotExist(payload.groupId)))
        } else if (!periodExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.RegistrationPeriodDoesNotExist(payload.periodId)))
        } else {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.REGISTRATION_GROUP_DELETED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.RegistrationGroupDeletedPayload(
              RegistrationGroupDeletedPayload().withGroupId(payload.groupId).withPeriodId(payload.periodId)
            ))
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
