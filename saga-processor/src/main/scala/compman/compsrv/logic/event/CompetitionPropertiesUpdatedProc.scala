package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitionPropertiesUpdatedEvent, Event}
import compman.compsrv.model.extension._

import scala.jdk.CollectionConverters.MapHasAsScala

object CompetitionPropertiesUpdatedProc {
  def apply[F[+_] : Monad : IdOperations : EventOperations, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitionPropertiesUpdatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad : IdOperations : EventOperations](
                                                                     event: CompetitionPropertiesUpdatedEvent,
                                                                     state: CompetitionState
                                                                   ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload <- event.payload
      props = payload.getProperties.asScala.toMap
      stateProps <- state.competitionProperties
      newState = state.createCopy(competitionProperties = Some(stateProps.applyProperties(props)))
    } yield newState
    Monad[F].pure(eventT)
  }
}
