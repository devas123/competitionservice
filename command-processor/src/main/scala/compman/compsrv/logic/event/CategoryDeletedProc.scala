package compman.compsrv.logic.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.logic.CompetitionState._
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryDeletedEvent, Event}

object CategoryDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: CategoryDeletedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CategoryDeletedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = (for {
    categoryId <- OptionT.fromOption[F](event.categoryId)
    res = state.deleteCategory(categoryId).fightsApply(_.map(_.filter(f => f._2.categoryId != categoryId)))
      .competitorsApply(_.map(_.map { case (str, competitor) =>
        (
          str,
          competitor
            .withCategories(Option(competitor.categories).map(_.filter(_ != categoryId)).getOrElse(Seq.empty))
        )
      }))

  } yield res).value
}
