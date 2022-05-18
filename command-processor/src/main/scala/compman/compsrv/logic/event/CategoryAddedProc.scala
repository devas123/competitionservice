package compman.compsrv.logic.event

import cats.Monad
import cats.data.OptionT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CategoryAddedEvent, Event}
import compservice.model.protobuf.model.CategoryDescriptor

object CategoryAddedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = {
    case x: CategoryAddedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CategoryAddedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT =
      for {
        payload <- OptionT.fromOption[F](event.payload)
        category = payload.getCategoryState
        cats <- OptionT.fromOption[F](state.categories.orElse(Some(Map.empty[String, CategoryDescriptor])))
      } yield state.copy(categories = Some(cats + (category.id -> category)))
    eventT.value
  }
}
