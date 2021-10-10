package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, RemoveCompetitorCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.CompetitorRemovedPayload

object RemoveCompetitorProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: RemoveCompetitorCommand =>
    process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: RemoveCompetitorCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _ <- assertETErr(
        state.competitors.exists(_.contains(payload.getCompetitorId)),
        Errors.CompetitorDoesNotExist(payload.getCompetitorId)
      )
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.COMPETITOR_REMOVED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(new CompetitorRemovedPayload().setFighterId(payload.getCompetitorId))
      ))

    } yield Seq(event)
    eventT.value
  }

}
