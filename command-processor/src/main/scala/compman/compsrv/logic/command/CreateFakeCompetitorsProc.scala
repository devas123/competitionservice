package compman.compsrv.logic.command

import cats.data.EitherT
import cats.Monad
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{CreateFakeCompetitors, InternalCommandProcessorCommand}
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload

object CreateFakeCompetitorsProc {

  def apply[F[+_]: Monad: IdOperations: EventOperations]()
    : PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ CreateFakeCompetitors(_, _) => addCompetitor(x)
  }

  private def addCompetitor[F[+_]: Monad: IdOperations: EventOperations](
    command: CreateFakeCompetitors
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import cats.implicits._
    val eventT = for {
      competitionId <- EitherT.fromOption[F](command.competitionId, Errors.NoCompetitionIdError())
      categoryId    <- EitherT.fromOption[F](command.categoryId, Errors.NoCategoryIdError())
      fighters = CompetitorService.generateRandomCompetitorsForCategory(5, 20, categoryId, competitionId)
      events <- EitherT.liftF[F, Errors.Error, List[Event]](fighters.traverse(competitor =>
        CommandEventOperations[F, Event].create(
          `type` = EventType.COMPETITOR_ADDED,
          competitorId = Some(competitor.id),
          competitionId = command.competitionId,
          categoryId = command.categoryId,
          payload = Some(
            MessageInfo.Payload.CompetitorAddedPayload(eventpayload.CompetitorAddedPayload().withCompetitor(competitor))
          )
        )
      ))
    } yield events
    eventT.value
  }
}
