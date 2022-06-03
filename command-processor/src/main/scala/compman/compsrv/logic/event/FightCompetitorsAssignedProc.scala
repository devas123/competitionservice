package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, FightCompetitorsAssignedEvent}
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object FightCompetitorsAssignedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: FightCompetitorsAssignedEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightCompetitorsAssignedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    import cats.implicits._
    val eventT = for {
      payload     <- event.payload
      assignments <- Option(payload.assignments)
      fights = state.fights
      updates = assignments.mapFilter(ass =>
        for {
          fromFight <- fights.get(ass.fromFightId)
          toFight   <- fights.get(ass.toFightId)
        } yield (ass, fromFight, toFight)
      ).mapFilter { case (ass, fromFight, toFight) =>
        for {
          scores <- Option(toFight.scores)
          score  <- scores.find(_.parentFightId.contains(fromFight.id))
          index               = scores.indexOf(score)
          parentReferenceType = Option(score.getParentReferenceType).getOrElse(ass.referenceType)
          newScore            = score.withCompetitorId(ass.competitorId).withParentReferenceType(parentReferenceType)
          newScores           = (scores.slice(0, index) :+ newScore) ++ scores.slice(index + 1, scores.length)
        } yield toFight.withScores(newScores)
      }
      newState = state.updateFights(updates)
    } yield newState
    Monad[F].pure(eventT)
  }
}
