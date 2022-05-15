package compman.compsrv.logic.command.stateless

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, RemoveAcademyCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{InvalidPayload, NoPayloadError}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}

object RemoveAcademyProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations]()
    : PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: RemoveAcademyCommand => removeAcademy(x)
  }

  private def removeAcademy[F[+_]: Monad: IdOperations: EventOperations](
    command: RemoveAcademyCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _       <- EitherT.fromOption(Option(payload.academyId), InvalidPayload(payload))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.ACADEMY_REMOVED,
        competitorId = None,
        competitionId = None,
        categoryId = None,
        payload = Some(MessageInfo.Payload.RemoveAcademyPayload(payload))
      ))
    } yield Seq(event)
    eventT.value
  }
}
