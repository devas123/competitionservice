package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits.toTraverseOps
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, DropAllBracketsCommand, DropBracketsCommand}
import compman.compsrv.model.events.{EventDTO, EventType}

object DropBracketsProc {
  def apply[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x: DropAllBracketsCommand => process(x.competitionId, x.categoryId, state)
    case x: DropBracketsCommand    => process(x.competitionId, x.categoryId, state)
  }

  private def process[F[+_]: CompetitionLogging.Service: Monad: IdOperations: EventOperations](
    competitionId: Option[String],
    categoryId: Option[String],
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    import compman.compsrv.logic.logging._
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      _          <- EitherT.liftF(info(s"Dropping all brackets"))
      categories <- EitherT.fromOption[F](state.categories, Errors.InternalError("No categories."))
      event <- categories.values.toList.filter(cat => categoryId.isEmpty || categoryId.contains(cat.getId))
        .traverse(cat =>
          EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
            `type` = EventType.CATEGORY_BRACKETS_DROPPED,
            competitorId = None,
            competitionId = competitionId,
            categoryId = Option(cat.getId),
            payload = None
          ))
        )
    } yield event
    eventT.value
  }
}
