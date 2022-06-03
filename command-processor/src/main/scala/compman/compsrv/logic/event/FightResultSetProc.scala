package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compservice.model.protobuf.model.CommandProcessorCompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, FightResultSet}
import compservice.model.protobuf.model.FightStatus

object FightResultSetProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: FightResultSet =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightResultSet,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      fightId     <- Option(payload.fightId)
      newScores   <- Option(payload.scores)
      fightResult <- payload.fightResult
      fights = state.fights
      fight <- fights.get(fightId)
      update = fight.withScores(newScores).withFightResult(fightResult)
        .withStatus(Option(payload.status).getOrElse(FightStatus.FINISHED))
      newState = state.fightsApply(_.map(m => m + (fightId -> update)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
