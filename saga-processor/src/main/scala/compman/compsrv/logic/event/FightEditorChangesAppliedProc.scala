package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, FightEditorChangesAppliedEvent}

object FightEditorChangesAppliedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightEditorChangesAppliedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightEditorChangesAppliedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import compman.compsrv.model.extension._
    val eventT = for {
      payload       <- event.payload
      updates       <- Option(payload.getUpdates).orElse(Some(Array.empty))
      removals      <- Option(payload.getRemovedFighids).orElse(Some(Array.empty))
      additions     <- Option(payload.getNewFights).orElse(Some(Array.empty))
      currentFights <- state.fights
      newFights = currentFights -- removals
      newState  = state.createCopy(fights = Some(newFights)).updateFights((updates ++ additions).toIndexedSeq)
    } yield newState
    Monad[F].pure(eventT)
  }
}
