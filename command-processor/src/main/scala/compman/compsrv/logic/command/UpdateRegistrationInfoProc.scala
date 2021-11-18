package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, UpdateRegistrationInfoCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.RegistrationInfoUpdatedPayload

object UpdateRegistrationInfoProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x: UpdateRegistrationInfoCommand =>
      process(x)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: UpdateRegistrationInfoCommand): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      event <-
        EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
          `type` = EventType.REGISTRATION_INFO_UPDATED,
          competitorId = None,
          competitionId = command.competitionId,
          categoryId = command.categoryId,
          payload = Some(new RegistrationInfoUpdatedPayload().setRegistrationInfo(payload.getRegistrationInfo))
        ))

    } yield Seq(event)
    eventT.value
  }

}
