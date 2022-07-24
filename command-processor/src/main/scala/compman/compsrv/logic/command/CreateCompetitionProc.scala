package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{CreateCompetitionCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.{
  NoCompetitionIdError,
  NoCompetitionPropertiesError,
  NoPayloadError,
  NoRegistrationInfoError
}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitionCreatedPayload

object CreateCompetitionProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations]()
    : PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ CreateCompetitionCommand(_, _, _) => process(x)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: CreateCompetitionCommand
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload       <- EitherT.fromOption(command.payload, NoPayloadError())
      regInfo       <- EitherT.fromOption(payload.reginfo, NoRegistrationInfoError())
      properties    <- EitherT.fromOption(payload.properties, NoCompetitionPropertiesError())
      competitionId <- EitherT.fromOption(command.competitionId, NoCompetitionIdError())
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITION_CREATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.CompetitionCreatedPayload(CompetitionCreatedPayload().update(
          _.reginfo    := regInfo.withId(competitionId),
          _.properties := properties.withId(competitionId)
        )))
      ))
    } yield Seq(event)
    eventT.value
  }
}
