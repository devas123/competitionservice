package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, RegistrationPeriodAddRegistrationGroupCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.events.payload.RegistrationInfoUpdatedPayload

import scala.jdk.CollectionConverters._

object RegistrationPeriodAddRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x: RegistrationPeriodAddRegistrationGroupCommand => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: RegistrationPeriodAddRegistrationGroupCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      periodExists = state.registrationInfo
        .exists(_.getRegistrationPeriods.containsKey(payload.getPeriodId))
      groupId = payload.getGroupId
      _ <- assertETErr(
        state.registrationInfo.exists(_.getRegistrationGroups.containsKey(groupId)),
        Errors.RegistrationGroupDoesNotExist(groupId)
      )
      period = state.registrationInfo.flatMap(_.getRegistrationPeriods.asScala.get(payload.getPeriodId))
        .get
      regInfo = state.registrationInfo.map { ri =>
        val newGroupIds = Option(period.getRegistrationGroupIds).getOrElse(Array.empty) :+ groupId
        val periods = ri.getRegistrationPeriods.asScala.toMap + (period.getId -> period.setRegistrationGroupIds(newGroupIds.distinct))
        ri.setRegistrationPeriods(periods.asJava)
      }
      event <-
        if (!periodExists) {
          EitherT.fromEither(Left[Errors.Error, EventDTO](Errors.RegistrationPeriodDoesNotExist(payload.getPeriodId)))
        } else {
          EitherT.liftF[F, Errors.Error, EventDTO](CommandEventOperations[F, EventDTO, EventType].create(
            `type` = EventType.REGISTRATION_INFO_UPDATED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(new RegistrationInfoUpdatedPayload().setRegistrationInfo(regInfo.get))
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
