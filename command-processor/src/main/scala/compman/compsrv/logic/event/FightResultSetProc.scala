package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, FightResultSet}
import compservice.model.protobuf.model.FightStatus

object FightResultSetProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[Event[Any], F[Option[CompetitionState]]] = { case x: FightResultSet => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightResultSet,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      fightId     <- Option(payload.fightId)
      newScores   <- Option(payload.scores)
      fightResult <- payload.fightResult
      fights      <- state.fights
      fight       <- fights.get(fightId)
      update = fight.withScores(newScores).withFightResult(fightResult)
        .withStatus(Option(payload.status).getOrElse(FightStatus.FINISHED))
      newState = state.fightsApply(_.map(m => m + (fightId -> update)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
