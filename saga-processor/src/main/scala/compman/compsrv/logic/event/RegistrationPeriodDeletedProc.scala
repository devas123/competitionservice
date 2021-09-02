package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitorAddedEvent, Event}
import compman.compsrv.model.{CompetitionState, Payload}

object RegistrationPeriodDeletedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[CompetitionState]] = {
    case x: CompetitorAddedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitorAddedEvent,
                                                                     state: CompetitionState
                                                                   ): F[CompetitionState] = {
    val eventT = for {
      payload <- event.payload
      newState = state.createCopy(
        competitors = state.competitors.map(_ + (payload.getFighter.getId -> payload.getFighter)),
        competitionProperties = state.competitionProperties,
        stages = state.stages,
        fights = state.fights,
        categories = state.categories,
        registrationInfo = state.registrationInfo,
        schedule = state.schedule,
        revision = state.revision + 1
      )
    } yield newState
    Monad[F].pure(eventT.getOrElse(state))
  }
}
