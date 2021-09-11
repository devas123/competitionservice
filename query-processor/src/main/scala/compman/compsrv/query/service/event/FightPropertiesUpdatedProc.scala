package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, FightPropertiesUpdatedEvent}

object FightPropertiesUpdatedProc {
  def apply[F[+_]: Monad, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightPropertiesUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad](
    event: FightPropertiesUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      update  <- Option(payload.getUpdate)
      fights  <- state.fights
      fight   <- fights.get(update.getFightId)
      updatedFight = fight.setMatId(Option(update.getMatId).getOrElse(fight.getMatId))
        .setNumberOnMat(Option(update.getNumberOnMat).getOrElse(fight.getNumberOnMat))
        .setStartTime(Option(update.getStartTime).getOrElse(fight.getStartTime))
      newState = state.createCopy(fights = Some(fights + (updatedFight.getId -> updatedFight)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
