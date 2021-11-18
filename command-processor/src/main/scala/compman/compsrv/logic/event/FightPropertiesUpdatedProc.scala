package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.event.Events.{Event, FightPropertiesUpdatedEvent}

object FightPropertiesUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightPropertiesUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightPropertiesUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import compman.compsrv.model.extensions._
    val eventT = for {
      payload <- event.payload
      update  <- Option(payload.getUpdate)
      fights  <- state.fights
      schedule <- state.schedule
      mats <- Option(schedule.mats)
      fight   <- fights.get(update.getFightId)
      updatedFight = fight.setMat(Option(update.getMatId).flatMap(mats.get).getOrElse(fight.getMat))
        .setNumberOnMat(Option(update.getNumberOnMat).getOrElse(fight.getNumberOnMat))
        .setStartTime(Option(update.getStartTime).getOrElse(fight.getStartTime))
      newState = state.copy(fights = Some(fights + (updatedFight.getId -> updatedFight)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
