package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.fight.createEmptyScore
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compman.compsrv.model.event.Events.{CompetitorsPropagatedToStageEvent, Event}
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, CompScore, FightReferenceType}

object CompetitorsPropagatedToStageProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = {
    case x: CompetitorsPropagatedToStageEvent => apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorsPropagatedToStageEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    import cats.implicits._
    val eventT = for {
      payload <- event.payload
      fights       = state.fights
      propagations = payload.propagations
      updatedFights = propagations.groupBy(_.toFightId).toList.mapFilter { case (fightId, assignments) =>
        val scores = assignments.toList.mapWithIndex((ass, index) =>
          CompScore().withCompetitorId(ass.competitorId).withScore(createEmptyScore).withOrder(index)
            .withParentFightId(ass.fromFightId).withParentReferenceType(FightReferenceType.PROPAGATED)
        )
        for { fight <- fights.get(fightId) } yield fight.withScores(scores)
      }
      newState = state.updateFights(updatedFights)
    } yield newState
    Monad[F].pure(eventT)
  }
}
