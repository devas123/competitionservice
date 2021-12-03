package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightCompetitorsAssignedEvent}

object FightCompetitorsAssignedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightCompetitorsAssignedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightCompetitorsAssignedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import cats.implicits._
    val eventT = for {
      payload     <- event.payload
      assignments <- Option(payload.getAssignments)
      fights      <- state.fights
      updates = assignments.toList.mapFilter(ass =>
        for {
          fromFight <- fights.get(ass.getFromFightId)
          toFight   <- fights.get(ass.getToFightId)
        } yield (ass, fromFight, toFight)
      ).mapFilter { case (ass, fromFight, toFight) =>
        for {
          scores <- Option(toFight.getScores)
          score  <- scores.find(_.getParentFightId == fromFight.getId)
          index               = scores.indexOf(score)
          parentReferenceType = Option(score.getParentReferenceType).getOrElse(ass.getReferenceType)
          newScore            = score.setCompetitorId(ass.getCompetitorId).setParentReferenceType(parentReferenceType)
          newScores           = (scores.slice(0, index) :+ newScore) ++ scores.slice(index + 1, scores.length)
        } yield toFight.setScores(newScores)
      }
      newState = state.updateFights(updates)
    } yield newState
    Monad[F].pure(eventT)
  }
}
