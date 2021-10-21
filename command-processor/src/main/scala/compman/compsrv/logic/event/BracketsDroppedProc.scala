package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CategoryBracketsDropped, Event}

object BracketsDroppedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: CategoryBracketsDropped => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CategoryBracketsDropped,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val maybeState = for {
      currentStages <- state.stages
      catId         <- event.categoryId
      newState = state.createCopy(stages = Option(currentStages.filter { case (_, o) => o.getCategoryId != catId }))
    } yield newState
    Monad[F].pure(maybeState)
  }
}
