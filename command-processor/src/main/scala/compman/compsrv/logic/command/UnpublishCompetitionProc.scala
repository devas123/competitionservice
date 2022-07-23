package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, UnpublishCompetitionCommand}
import compman.compsrv.model.Errors.NoCompetitionIdError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, CompetitionStatus}

object UnpublishCompetitionProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: UnpublishCompetitionCommand => process(state, x)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState,
    command: UnpublishCompetitionCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      competitionId <- EitherT.fromOption(command.competitionId, NoCompetitionIdError())
      properties    <- EitherT.fromOption(state.competitionProperties, Errors.InternalError())
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITION_PROPERTIES_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = None,
        payload =
          Some(MessageInfo.Payload.CompetitionPropertiesUpdatedPayload(CompetitionPropertiesUpdatedPayload().update(
            _.properties := properties.withStatus(CompetitionStatus.UNPUBLISHED)
          )))
      ))
    } yield Seq(event)
    eventT.value
  }
}
