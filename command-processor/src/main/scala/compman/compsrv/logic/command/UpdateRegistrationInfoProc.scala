package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, UpdateRegistrationInfoCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.RegistrationInfoUpdatedPayload

object UpdateRegistrationInfoProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P](): PartialFunction[InternalCommandProcessorCommand[P], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: UpdateRegistrationInfoCommand => process(x)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: UpdateRegistrationInfoCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
        `type` = EventType.REGISTRATION_INFO_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.RegistrationInfoUpdatedPayload(
          RegistrationInfoUpdatedPayload().withRegistrationInfo(payload.getRegistrationInfo)
        ))
      ))

    } yield Seq(event)
    eventT.value
  }

}
