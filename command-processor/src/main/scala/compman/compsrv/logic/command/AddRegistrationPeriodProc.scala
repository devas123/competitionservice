package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{AddRegistrationPeriodCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.RegistrationPeriodAddedPayload
import compman.compsrv.model.Errors.{InternalError, NoPayloadError}

object AddRegistrationPeriodProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
                                                                          state: CompetitionState
                                                                        ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ AddRegistrationPeriodCommand(_, _, _) =>
      addRegistrationPeriod(x, state)
  }


  private def addRegistrationPeriod[F[+_] : Monad : IdOperations : EventOperations](
                                                                   command: AddRegistrationPeriodCommand,
                                                                   state: CompetitionState
                                                                 ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        id <- EitherT
          .liftF[F, Errors.Error, String](IdOperations[F].registrationPeriodId(payload.getPeriod))
        exists <- EitherT.right(Monad[F].pure((for {
            regInfo <- state.registrationInfo
            periods <- Option(regInfo.getRegistrationPeriods)
          } yield periods.exists(_.getId == id)).getOrElse(false)))
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.REGISTRATION_PERIOD_ADDED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = None,
                payload = Some(new RegistrationPeriodAddedPayload(payload.getPeriod))
              )
            )
          } else {
            EitherT(
              CommandEventOperations[F, EventDTO, EventType]
                .error(InternalError())
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
