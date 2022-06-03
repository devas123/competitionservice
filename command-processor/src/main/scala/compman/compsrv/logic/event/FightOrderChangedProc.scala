package compman.compsrv.logic.event

import cats.Monad
import cats.implicits._
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{Event, FightOrderChangedEvent}
import compman.compsrv.Utils
import compman.compsrv.logic.fight.CommonFightUtils
import compman.compsrv.logic.CompetitionState.CompetitionStateOps
import compservice.model.protobuf.model.CommandProcessorCompetitionState

object FightOrderChangedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[Event[Any], F[Option[CommandProcessorCompetitionState]]] = { case x: FightOrderChangedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: FightOrderChangedEvent,
    state: CommandProcessorCompetitionState
  ): F[Option[CommandProcessorCompetitionState]] = {
    import CommonFightUtils._
    val eventT = for {
      payload <- event.payload
      currentFights = state.fights
      fight    <- currentFights.get(payload.fightId)
      schedule <- state.schedule
      mats     <- Option(schedule.mats).map(m => Utils.groupById(m)(_.id))
      updates = CommonFightUtils.generateUpdates(payload, fight, currentFights)
      newFights <- Option(updates.mapFilter { case (id, update) =>
        for {
          f   <- currentFights.get(id)
          mat <- mats.get(update.matId)
        } yield f.withMat(mat).withStartTime(update.getStartTime).withNumberOnMat(update.numberOnMat)
      })
      newState = state.updateFights((currentFights ++ newFights.map(f => f.id -> f)).values.toSeq)
    } yield newState
    Monad[F].pure(eventT)
  }
}
