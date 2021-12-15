package compman.compsrv.logic.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{CategoryDeletedEvent, Event}

object CategoryDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: CategoryDeletedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CategoryDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = (for {
    categoryId <- OptionT.fromOption[F](event.categoryId)
    res = state.deleteCategory(categoryId).fightsApply(_.map(_.filter(f => f._2.getCategoryId != categoryId)))
      .competitorsApply(_.map(_.map { case (str, competitor) =>
        (
          str,
          competitor
            .setCategories(Option(competitor.getCategories).map(_.filter(_ != categoryId)).getOrElse(Array.empty))
        )
      }))

  } yield res).value
}
