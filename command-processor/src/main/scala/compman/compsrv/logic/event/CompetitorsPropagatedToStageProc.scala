package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.fight.createEmptyScore
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.event.Events.{CompetitorsPropagatedToStageEvent, Event}

import scala.jdk.CollectionConverters.ListHasAsScala

object CompetitorsPropagatedToStageProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: CompetitorsPropagatedToStageEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: CompetitorsPropagatedToStageEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import cats.implicits._
    import compman.compsrv.model.extensions._
    val eventT = for {
      payload <- event.payload
      fights  <- state.fights
      propagations = payload.getPropagations.asScala
      updatedFights = propagations.groupBy(_.getToFightId).toList.mapFilter { case (fightId, assignments) =>
        val scores = assignments.toList.mapWithIndex((ass, index) =>
          new CompScoreDTO().setCompetitorId(ass.getCompetitorId).setScore(createEmptyScore).setOrder(index)
            .setParentFightId(ass.getFromFightId).setParentReferenceType(FightReferenceType.PROPAGATED)
        )
        for { fight <- fights.get(fightId) } yield fight.setScores(scores.toArray)
      }
      newState = state.updateFights(updatedFights)
    } yield newState
    Monad[F].pure(eventT)
  }
}
