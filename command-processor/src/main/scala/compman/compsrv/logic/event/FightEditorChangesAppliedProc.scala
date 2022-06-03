package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, FightEditorChangesAppliedEvent}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object FightEditorChangesAppliedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: FightEditorChangesAppliedEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightEditorChangesAppliedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload   <- event.payload
      updates   <- Option(payload.updates).orElse(Some(Seq.empty))
      removals  <- Option(payload.removedFighids).orElse(Some(Seq.empty))
      additions <- Option(payload.newFights).orElse(Some(Seq.empty))
      currentFights = state.fights
      newFights     = currentFights -- removals
      newState      = state.copy(fights = newFights).updateFights((updates ++ additions).toIndexedSeq)
    } yield newState
    Monad[F].pure(eventT)
  }
}
