package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{BracketsGeneratedEvent, Event}

object BracketsGeneratedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: BracketsGeneratedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: BracketsGeneratedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      currentStages <- state.stages
      newState = state.createCopy(stages = Some(currentStages ++ payload.getStages.map(s => s.getId -> s)), revision = state.revision + 1)
    } yield newState
    Monad[F].pure(eventT)
  }
}
