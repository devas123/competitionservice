package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, DeleteRegistrationPeriodCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.RegistrationPeriodDeletedPayload

object DeleteRegistrationPeriodProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ DeleteRegistrationPeriodCommand(_, _, _) =>
      process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: DeleteRegistrationPeriodCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, NoPayloadError())
        periodExists = state
          .registrationInfo
          .exists(_.getRegistrationPeriods.exists(per => payload.getPeriodId == per.getId))
        event <-
          if (!periodExists) {
            EitherT.fromEither(
              Left[Errors.Error, EventDTO](
                Errors.RegistrationPeriodDoesNotExist(payload.getPeriodId)
              )
            )
          } else {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.REGISTRATION_PERIOD_DELETED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(
                  new RegistrationPeriodDeletedPayload()
                    .setPeriodId(payload.getPeriodId)
                )
              )
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
