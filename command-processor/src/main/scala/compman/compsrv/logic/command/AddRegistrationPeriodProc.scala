package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{AddRegistrationPeriodCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.{NoPayloadError, RegistrationPeriodAlreadyExistsError}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload

object AddRegistrationPeriodProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ AddRegistrationPeriodCommand(_, _, _) => addRegistrationPeriod(x, state)
  }

  private def addRegistrationPeriod[F[+_]: Monad: IdOperations: EventOperations](
    command: AddRegistrationPeriodCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      id      <- EitherT.liftF[F, Errors.Error, String](IdOperations[F].registrationPeriodId(payload.getPeriod))
      periodExists <- EitherT.right(Monad[F].pure(
        (for {
          regInfo <- state.registrationInfo
          periods <- Option(regInfo.registrationPeriods)
        } yield periods.contains(id)).getOrElse(false)
      ))
      event <-
        if (!periodExists) {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.REGISTRATION_PERIOD_ADDED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = None,
            payload = Some(MessageInfo.Payload.RegistrationPeriodAddedPayload(
              eventpayload.RegistrationPeriodAddedPayload(payload.period.map(_.withId(id)))
            ))
          ))
        } else { EitherT(CommandEventOperations[F, Event].error(RegistrationPeriodAlreadyExistsError(id))) }
    } yield Seq(event)
    eventT.value
  }
}
