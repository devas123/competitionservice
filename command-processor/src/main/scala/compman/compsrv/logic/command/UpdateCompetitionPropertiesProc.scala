package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, UpdateCompetitionProperties}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.CompetitionPropertiesUpdatedPayload

object UpdateCompetitionPropertiesProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: UpdateCompetitionProperties =>
    process(x, state)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    command: UpdateCompetitionProperties,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      _       <- EitherT.liftF(info(s"Updating competition properties: $payload"))
      event <- EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.COMPETITION_PROPERTIES_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(new CompetitionPropertiesUpdatedPayload().setProperties(payload.getCompetitionProperties))
      ))
    } yield Seq(event)
    eventT.value
  }
}
