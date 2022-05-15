package compman.compsrv.logic.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryDeletedEvent, Event}

object CategoryDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: CategoryDeletedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CategoryDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = (for {
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
