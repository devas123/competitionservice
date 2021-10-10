package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{AddCompetitorCommand, Command}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.Errors.{CompetitorAlreadyExists, InternalError}

object AddCompetitorProc {

  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ AddCompetitorCommand(_, _, _) =>
      addCompetitor(x, state)
  }

  private def addCompetitor[F[+_]: Monad: IdOperations: EventOperations](
      command: AddCompetitorCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption(command.payload, InternalError())
        id <- EitherT
          .liftF[F, Errors.Error, String](IdOperations[F].competitorId(payload.getCompetitor))
        exists <- EitherT
          .fromOption(state.competitors.map(_.contains(id)), Errors.InternalError())
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.COMPETITOR_ADDED,
                competitorId = Some(id),
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(new CompetitorAddedPayload(payload.getCompetitor))
              )
            )
          } else {
            EitherT(
              CommandEventOperations[F, EventDTO, EventType]
                .error(CompetitorAlreadyExists(id, payload.getCompetitor))
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
