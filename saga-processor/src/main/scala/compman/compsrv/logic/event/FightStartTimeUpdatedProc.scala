package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{Event, FightStartTimeUpdatedEvent}

object FightStartTimeUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: FightStartTimeUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightStartTimeUpdatedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    import cats.implicits._
    import compman.compsrv.model.extension._
    val eventT = for {
      payload   <- event.payload
      newFights <- Option(payload.getNewFights)
      fights    <- state.fights
      updates = newFights.toList.mapFilter(fstp =>
        for { fight <- fights.get(fstp.getFightId) } yield fight.setInvalid(fstp.getInvalid).setMatId(fstp.getMatId)
          .setPeriod(fstp.getPeriodId).setStartTime(fstp.getStartTime).setNumberOnMat(fstp.getNumberOnMat)
          .setScheduleEntryId(fstp.getScheduleEntryId)
      )
      newState = state.updateFights(updates)
    } yield newState
    Monad[F].pure(eventT)
  }
}