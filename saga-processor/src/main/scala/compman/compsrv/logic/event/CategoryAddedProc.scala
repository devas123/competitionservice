package compman.compsrv.logic.event

import cats.Monad
import cats.data.OptionT
import cats.implicits._
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.event.Events.{CategoryAddedEvent, Event}
import compman.compsrv.model.{CompetitionState, Payload}

object CategoryAddedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[CompetitionState]] = {
    case x: CategoryAddedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CategoryAddedEvent,
                                                                     state: CompetitionState
                                                                   ): F[CompetitionState] = {
    val eventT =
      for {
        payload <- OptionT.fromOption[F](event.payload)
        category = payload.getCategoryState
        cats <- OptionT.fromOption[F](state.categories.orElse(Some(Map.empty[String, CategoryDescriptorDTO])))
      } yield state.createCopy(categories = Some(cats + (category.getId -> category)))
    eventT.value.map(_.getOrElse(state))
  }
}
