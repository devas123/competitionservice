package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, RemoveCompetitorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitorRemovedPayload
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object RemoveCompetitorProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: RemoveCompetitorCommand => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: RemoveCompetitorCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      competitor <- EitherT.fromOption(state.competitors.get(payload.competitorId), Errors.CompetitorDoesNotExist(payload.competitorId))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITOR_REMOVED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(
          MessageInfo.Payload.CompetitorRemovedPayload(CompetitorRemovedPayload()
            .withFighterId(payload.competitorId)
            .withCategories(competitor.categories)
          )
        )
      ))

    } yield Seq(event)
    eventT.value
  }

}
