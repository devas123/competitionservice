package compman.compsrv.logic.command.stateless

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{AddAcademyCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.{InvalidPayload, NoPayloadError}

object AddAcademyProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload]()
    : PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: AddAcademyCommand =>
    addAcademy(x)
  }

  private def addAcademy[F[+_]: Monad: IdOperations: EventOperations](
    command: AddAcademyCommand
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      academy <- EitherT.fromOption(Option(payload.getAcademy), InvalidPayload(payload))
      id      <- EitherT.liftF[F, Errors.Error, String](IdOperations[F].uid)
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.ACADEMY_ADDED,
        competitorId = None,
        competitionId = None,
        categoryId = None,
        payload = Some(payload.setAcademy(academy.setId(id)))
      ))
    } yield Seq(event)
    eventT.value
  }
}
