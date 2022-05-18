package compman.compsrv.logic.command.stateless

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{AddAcademyCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{InvalidPayload, NoPayloadError}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}

object AddAcademyProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations]()
    : PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: AddAcademyCommand => addAcademy(x)
  }

  private def addAcademy[F[+_]: Monad: IdOperations: EventOperations](
    command: AddAcademyCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      academy <- EitherT.fromOption(Option(payload.getAcademy), InvalidPayload(command))
      id      <- EitherT.liftF[F, Errors.Error, String](IdOperations[F].uid)
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.ACADEMY_ADDED,
        competitorId = None,
        competitionId = None,
        categoryId = None,
        payload = Some(MessageInfo.Payload.AddAcademyPayload(payload.withAcademy(academy.withId(id))))
      ))
    } yield Seq(event)
    eventT.value
  }
}
