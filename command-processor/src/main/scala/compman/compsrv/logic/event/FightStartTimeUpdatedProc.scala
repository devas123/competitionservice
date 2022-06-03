package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, FightStartTimeUpdatedEvent}
import compman.compsrv.Utils
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object FightStartTimeUpdatedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: FightStartTimeUpdatedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightStartTimeUpdatedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    import cats.implicits._
    val eventT = for {
      payload   <- event.payload
      newFights <- Option(payload.newFights)
      fights = state.fights
      schedule <- state.schedule
      mats     <- Option(schedule.mats).map(ms => Utils.groupById(ms)(_.id))
      updates = newFights.toList.mapFilter(fstp =>
        for { fight <- fights.get(fstp.fightId) } yield fight.withInvalid(fstp.invalid).withMat(mats(fstp.matId))
          .withPeriod(fstp.periodId).withStartTime(fstp.getStartTime).withNumberOnMat(fstp.numberOnMat)
          .withScheduleEntryId(fstp.scheduleEntryId)
      )
      newState = state.updateFights(updates)
    } yield newState
    Monad[F].pure(eventT)
  }
}
