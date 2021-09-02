package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.event.Events.{CompetitionCreatedEvent, Event}
import compman.compsrv.model.{CompetitionState, Payload}

object CompetitionCreatedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitionCreatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitionCreatedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      props = payload.getProperties
      regInfo = payload.getReginfo
      newState = state.createCopy(competitionProperties = Some(props), registrationInfo = Some(regInfo))
    } yield newState
    Monad[F].pure(eventT)
  }
}
