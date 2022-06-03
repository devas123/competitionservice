package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits.toTraverseOps
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.command.Commands.{DropAllBracketsCommand, DropBracketsCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object DropBracketsProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: DropAllBracketsCommand => process(x.competitionId, None, state)
    case x: DropBracketsCommand    => process(x.competitionId, x.categoryId, state)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    competitionId: Option[String],
    categoryId: Option[String],
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      _ <- EitherT.liftF(info(s"Dropping all brackets"))
      categories = state.categories
      event <- categories.values.toList.filter(cat => categoryId.isEmpty || categoryId.contains(cat.id)).traverse(cat =>
        EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
          `type` = EventType.CATEGORY_BRACKETS_DROPPED,
          competitorId = None,
          competitionId = competitionId,
          categoryId = Option(cat.id),
          payload = None
        ))
      )
    } yield event
    eventT.value
  }
}
