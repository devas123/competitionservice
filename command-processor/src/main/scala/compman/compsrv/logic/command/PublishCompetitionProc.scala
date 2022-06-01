package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, PublishCompetitionCommand}
import compman.compsrv.model.Errors.NoCompetitionIdError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload
import compservice.model.protobuf.model.CompetitionStatus

object PublishCompetitionProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: PublishCompetitionCommand => process(state, x)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    state: CompetitionState,
    command: PublishCompetitionCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      competitionId <- EitherT.fromOption(command.competitionId, NoCompetitionIdError())
      properties <- EitherT.fromOption(state.competitionProperties, Errors.InternalError())
      _             <- EitherT.liftF(info(s"Publishing competition: $competitionId"))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITION_PROPERTIES_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = None,
        payload = Some(MessageInfo.Payload.CompetitionPropertiesUpdatedPayload(
          CompetitionPropertiesUpdatedPayload().update(_.properties := properties.withStatus(CompetitionStatus.PUBLISHED))
        ))
      ))
    } yield Seq(event)
    eventT.value
  }
}
