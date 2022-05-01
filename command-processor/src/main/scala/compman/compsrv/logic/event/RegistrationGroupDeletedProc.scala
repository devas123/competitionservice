package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.{RegistrationGroupDTO, RegistrationPeriodDTO}
import compman.compsrv.model.event.Events.{Event, RegistrationGroupDeletedEvent}

import scala.jdk.CollectionConverters._

object RegistrationGroupDeletedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationGroupDeletedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupDeletedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload    <- event.payload
      regInfo    <- state.registrationInfo
      regPeriods <- Option(regInfo.getRegistrationPeriods).orElse(Some(Map.empty[String, RegistrationPeriodDTO].asJava))
      regGroups  <- Option(regInfo.getRegistrationGroups).orElse(Some(Map.empty[String, RegistrationGroupDTO].asJava))
      newPeriods = regPeriods.asScala.map { case (id, p) =>
        val regGrIds = Option(p.getRegistrationGroupIds).getOrElse(Array.empty[String])
        (id, p.setRegistrationGroupIds(regGrIds.filter(_ != payload.getGroupId)))
      }
      newGroups = regGroups.asScala.toMap - payload.getGroupId
      newState = state.copy(registrationInfo =
        Some(regInfo.setRegistrationGroups(newGroups.asJava).setRegistrationPeriods(newPeriods.asJava))
      )
    } yield newState
    Monad[F].pure(eventT)
  }
}
