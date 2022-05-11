package compman.compsrv.logic.command.stateless

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, RemoveAcademyCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.{InvalidPayload, NoPayloadError}

object RemoveAcademyProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload]()
    : PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: RemoveAcademyCommand =>
    removeAcademy(x)
  }

  private def removeAcademy[F[+_]: Monad: IdOperations: EventOperations](
    command: RemoveAcademyCommand
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _      <- EitherT.fromOption(Option(payload.getAcademyId), InvalidPayload(payload))
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.ACADEMY_REMOVED,
        competitorId = None,
        competitionId = None,
        categoryId = None,
        payload = Some(payload)
      ))
    } yield Seq(event)
    eventT.value
  }
}
