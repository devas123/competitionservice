package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.command.Commands.{
  InternalCommandProcessorCommand,
  RegistrationPeriodAddRegistrationGroupCommand
}
import compman.compsrv.model.Errors
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.RegistrationInfoUpdatedPayload

object RegistrationPeriodAddRegistrationGroupProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: RegistrationPeriodAddRegistrationGroupCommand => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: RegistrationPeriodAddRegistrationGroupCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      periodExists = state.registrationInfo.exists(_.registrationPeriods.contains(payload.periodId))
      groupId      = payload.groupId
      _ <- assertETErr(
        state.registrationInfo.exists(_.registrationGroups.contains(groupId)),
        Errors.RegistrationGroupDoesNotExist(groupId)
      )
      period = state.registrationInfo.flatMap(_.registrationPeriods.get(payload.periodId)).get
      regInfo = state.registrationInfo.map { ri =>
        val newGroupIds = Option(period.registrationGroupIds).getOrElse(Seq.empty) :+ groupId
        val periods     = ri.registrationPeriods + (period.id -> period.withRegistrationGroupIds(newGroupIds.distinct))
        ri.withRegistrationPeriods(periods)
      }
      event <-
        if (!periodExists) {
          EitherT.fromEither(Left[Errors.Error, Event](Errors.RegistrationPeriodDoesNotExist(payload.periodId)))
        } else {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
            `type` = EventType.REGISTRATION_INFO_UPDATED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.RegistrationInfoUpdatedPayload(
              RegistrationInfoUpdatedPayload().withRegistrationInfo(regInfo.get)
            ))
          ))
        }
    } yield Seq(event)
    eventT.value
  }
}
