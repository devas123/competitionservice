package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{CreateCompetitionCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitionCreatedPayload

object CreateCompetitionProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ CreateCompetitionCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    command: CreateCompetitionCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _       <- EitherT.liftF(info(s"Creating competition: ${payload.getProperties}"))
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITION_CREATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.CompetitionCreatedPayload(CompetitionCreatedPayload().update(
          _.reginfo.setIfDefined(payload.reginfo.orElse(state.registrationInfo)),
          _.properties.setIfDefined(payload.properties.orElse(state.competitionProperties))
        )))
      ))
    } yield Seq(event)
    eventT.value
  }
}
