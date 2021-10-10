package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, CreateFakeCompetitors}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.CompetitorAddedPayload

object CreateFakeCompetitorsProc {

  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload]()
    : PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x @ CreateFakeCompetitors(_, _) =>
    addCompetitor(x)
  }

  private def addCompetitor[F[+_]: Monad: IdOperations: EventOperations](
    command: CreateFakeCompetitors
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import cats.implicits._
    val eventT = for {
      competitionId <- EitherT.fromOption[F](command.competitionId, Errors.NoCompetitionIdError())
      categoryId    <- EitherT.fromOption[F](command.categoryId, Errors.NoCategoryIdError())
      fighters = CompetitorService.generateRandomCompetitorsForCategory(5, 20, categoryId, competitionId)
      events <- EitherT.liftF[F, Errors.Error, List[EventDTO]](fighters.traverse(competitor =>
        CommandEventOperations[F, EventDTO, EventType].create(
          `type` = EventType.COMPETITOR_ADDED,
          competitorId = Some(competitor.getId),
          competitionId = command.competitionId,
          categoryId = command.categoryId,
          payload = Some(new CompetitorAddedPayload(competitor))
        )
      ))
    } yield events
    eventT.value
  }
}
