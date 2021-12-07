package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.event.Events.{Event, FightResultSet}

object FightResultSetProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightResultSet => apply[F](x, state) }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightResultSet,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload     <- event.payload
      fightId     <- Option(payload.getFightId)
      newScores   <- Option(payload.getScores)
      fightResult <- Option(payload.getFightResult)
      fights      <- state.fights
      fight       <- fights.get(fightId)
      update   = fight.setScores(newScores).setFightResult(fightResult).setStatus(Option(payload.getStatus).getOrElse(FightStatus.FINISHED))
      newState = state.fightsApply(_.map(m => m + (fightId -> update)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
