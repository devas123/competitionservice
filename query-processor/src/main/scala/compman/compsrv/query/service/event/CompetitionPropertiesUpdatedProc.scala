package compman.compsrv.query.service.event

import cats.Monad
import compman.compsrv.model.{CompetitionState, Payload}
import compman.compsrv.model.event.Events.{CompetitionPropertiesUpdatedEvent, Event}
import compman.compsrv.model.extensions._

import scala.jdk.CollectionConverters.MapHasAsScala

object CompetitionPropertiesUpdatedProc {
  def apply[F[+_] : Monad, P <: Payload](
                                                                           state: CompetitionState
                                                                         ): PartialFunction[Event[P], F[Option[CompetitionState]]] = {
    case x: CompetitionPropertiesUpdatedEvent =>
      apply[F](x, state)
  }

  private def apply[F[+_] : Monad](
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
