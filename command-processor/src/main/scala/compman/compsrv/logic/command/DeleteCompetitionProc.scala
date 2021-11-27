package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, DeleteCompetition}
import compman.compsrv.model.events.{EventDTO, EventType}

object DeleteCompetitionProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload]()
    : PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: DeleteCompetition => process(x) }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: DeleteCompetition
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.COMPETITION_DELETED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = None
      ))
    } yield Seq(event)
    eventT.value
  }
}
