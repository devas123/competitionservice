package compman.compsrv.logic.command.stateless

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, UpdateAcademyCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{InvalidPayload, NoPayloadError}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}

object UpdateAcademyProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations]()
    : PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: UpdateAcademyCommand => updateAcademy(x)
  }

  private def updateAcademy[F[+_]: Monad: IdOperations: EventOperations](
    command: UpdateAcademyCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      academy <- EitherT.fromOption(Option(payload.getAcademy), InvalidPayload(payload))
      _       <- EitherT.fromOption(Option(academy.id), InvalidPayload(payload))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
        `type` = EventType.ACADEMY_UPDATED,
        competitorId = None,
        competitionId = None,
        categoryId = None,
        payload = Some(MessageInfo.Payload.UpdateAcademyPayload(payload))
      ))
    } yield Seq(event)
    eventT.value
  }
}
