package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{DeleteRegistrationPeriodCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{RegistrationGroupDeletedPayload, RegistrationPeriodDeletedPayload}

object DeleteRegistrationPeriodProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ DeleteRegistrationPeriodCommand(_, _, _) => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: DeleteRegistrationPeriodCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      periodExists        = state.registrationInfo.exists(_.registrationPeriods.contains(payload.periodId))
      registrationPeriods = state.registrationInfo.map(_.registrationPeriods).getOrElse(Map.empty)

      groupsOfPeriod = state.registrationInfo.map { r =>
        r.registrationPeriods.values.flatMap(_.registrationGroupIds).toList
      }.getOrElse(List.empty)
      groupsWithoutPeriodDeleted <- EitherT.liftF[F, Errors.Error, List[Event]](
        groupsOfPeriod.filter { gr =>
          !registrationPeriods.exists { case (id, p) => id != payload.periodId && p.registrationGroupIds.contains(gr) }
        }.traverse { grId =>
          CommandEventOperations[F, Event, EventType].create(
            `type` = EventType.REGISTRATION_GROUP_DELETED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.RegistrationGroupDeletedPayload(
              RegistrationGroupDeletedPayload().withPeriodId(payload.periodId).withGroupId(grId)
            ))
          )
        }
      )
      event <-
        if (!periodExists) {
          EitherT.fromEither[F](Left[Errors.Error, Event](Errors.RegistrationPeriodDoesNotExist(payload.periodId)))
        } else {
          EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event, EventType].create(
            `type` = EventType.REGISTRATION_PERIOD_DELETED,
            competitorId = None,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.RegistrationPeriodDeletedPayload(
              RegistrationPeriodDeletedPayload().withPeriodId(payload.periodId)
            ))
          ))
        }
    } yield groupsWithoutPeriodDeleted ++ Seq(event)
    eventT.value
  }
}
