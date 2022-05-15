package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, UpdateCompetitionProperties}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitionPropertiesUpdatedPayload

object UpdateCompetitionPropertiesProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: UpdateCompetitionProperties => process(x)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    command: UpdateCompetitionProperties): F[Either[Errors.Error, Seq[Event]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _       <- EitherT.liftF(info(s"Updating competition properties: $payload"))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
        `type` = EventType.COMPETITION_PROPERTIES_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.CompetitionPropertiesUpdatedPayload(
          CompetitionPropertiesUpdatedPayload().update(_.properties.setIfDefined(payload.competitionProperties))
        ))
      ))
    } yield Seq(event)
    eventT.value
  }
}
