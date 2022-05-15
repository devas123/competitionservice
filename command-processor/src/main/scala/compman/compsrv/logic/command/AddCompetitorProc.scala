package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{AddCompetitorCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{CompetitorAlreadyExists, InternalError}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitorAddedPayload

object AddCompetitorProc {

  def apply[F[+_]: Monad: IdOperations: EventOperations](
      state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ AddCompetitorCommand(_, _, _) =>
      addCompetitor(x, state)
  }

  private def addCompetitor[F[+_]: Monad: IdOperations: EventOperations](
      command: AddCompetitorCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] =
      for {
        payload <- EitherT.fromOption(command.payload, InternalError())
        id <- EitherT
          .liftF[F, Errors.Error, String](IdOperations[F].competitorId(payload.getCompetitor))
        exists <- EitherT
          .fromOption(state.competitors.map(_.contains(id)), Errors.InternalError())
        event <-
          if (exists) {
            EitherT.liftF[F, Errors.Error, Event](
              CommandEventOperations[F, Event, EventType].create(
                `type` = EventType.COMPETITOR_ADDED,
                competitorId = Some(id),
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(MessageInfo.Payload.CompetitorAddedPayload(CompetitorAddedPayload(payload.competitor)))
              )
            )
          } else {
            EitherT(
              CommandEventOperations[F, Event, EventType]
                .error(CompetitorAlreadyExists(id, payload.getCompetitor))
            )
          }
      } yield Seq(event)
    eventT.value
  }
}
