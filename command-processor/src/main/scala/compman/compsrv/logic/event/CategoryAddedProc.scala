package compman.compsrv.logic.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryAddedEvent, Event}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object CategoryAddedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: CategoryAddedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CategoryAddedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload <- OptionT.fromOption[F](event.payload)
      category = payload.getCategoryState
      cats     = state.categories
    } yield state.withCategories(cats + (category.id -> category))
    eventT.value
  }
}
