package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{AddCompetitorCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.{CompetitorAlreadyExists, InternalError, InvalidPayload}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.CompetitorAddedPayload
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object AddCompetitorProc {

  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ AddCompetitorCommand(_, _, _) => addCompetitor(x, state)
  }

  private def addCompetitor[F[+_]: Monad: IdOperations: EventOperations](
    command: AddCompetitorCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload         <- EitherT.fromOption(command.payload, InternalError())
      competitorToAdd <- EitherT.fromOption(command.payload.flatMap(_.competitor), InvalidPayload(payload))
      id              <- EitherT.liftF[F, Errors.Error, String](IdOperations[F].competitorId(competitorToAdd))
      exists      = state.competitors.contains(id)
      emailExists = state.competitors.values.exists(_.email.equalsIgnoreCase(competitorToAdd.email.trim))
      event <-
        if (!exists && !emailExists) {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.COMPETITOR_ADDED,
            competitorId = Some(id),
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(
              MessageInfo.Payload.CompetitorAddedPayload(CompetitorAddedPayload(payload.competitor.map(_.withId(id))))
            )
          ))
        } else { EitherT(CommandEventOperations[F, Event].error(CompetitorAlreadyExists(id, payload.getCompetitor))) }
    } yield Seq(event)
    eventT.value
  }
}
