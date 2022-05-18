package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{BracketsGeneratedEvent, Event}

object BracketsGeneratedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: BracketsGeneratedEvent => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: BracketsGeneratedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload       <- event.payload
      currentStages <- state.stages
      newState = state.copy(stages = Some(currentStages ++ payload.stages.map(s => s.id -> s)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
