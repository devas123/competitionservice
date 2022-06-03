package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, UpdateCompetitorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitorUpdatedPayload
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object UpdateCompetitorProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: UpdateCompetitorCommand => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: UpdateCompetitorCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _ <- assertETErr(
        state.competitors.contains(payload.getCompetitor.id),
        Errors.CompetitorDoesNotExist(payload.getCompetitor.id)
      )
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITOR_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.CompetitorUpdatedPayload(
          CompetitorUpdatedPayload().update(_.competitor.setIfDefined(payload.competitor))
        ))
      ))

    } yield Seq(event)
    eventT.value
  }

}
