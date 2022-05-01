package compman.compsrv.logic.event

import cats.Monad
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.event.Events.{Event, RegistrationGroupAddedEvent}

import scala.jdk.CollectionConverters._

object RegistrationGroupAddedProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Event[P], F[Option[CompetitionState]]] = { case x: RegistrationGroupAddedEvent =>
    apply[F](x, state)
  }

  private def apply[F[+_]: Monad: IdOperations: EventOperations](
    event: RegistrationGroupAddedEvent,
    state: CompetitionState
  ): F[Option[CompetitionState]] = {
    val eventT = for {
      payload             <- event.payload
      regInfo             <- state.registrationInfo
      regPeriods          <- Option(regInfo.getRegistrationPeriods)
      regPeriod           <- regPeriods.asScala.get(payload.getPeriodId)
      addedGroups         <- Option(payload.getGroups)
      currentPeriodGroups <- Option(regPeriod.getRegistrationGroupIds).orElse(Some(Array.empty[String]))
      regGroups <- Option(regInfo.getRegistrationGroups).orElse(Some(Map.empty[String, RegistrationGroupDTO].asJava))
      updatedPeriod = regPeriod.setRegistrationGroupIds((currentPeriodGroups ++ addedGroups.map(_.getId)).distinct)
      newPeriods = regPeriods.asScala.toMap + (payload.getPeriodId -> updatedPeriod)
      newGroups  = regGroups.asScala.toMap ++ addedGroups.map(g => g.getId -> g)
      newRegInfo = regInfo.setRegistrationPeriods(newPeriods.asJava).setRegistrationGroups(newGroups.asJava)
      newState   = state.copy(registrationInfo = Some(newRegInfo))
    } yield newState
    Monad[F].pure(eventT)
  }
}
