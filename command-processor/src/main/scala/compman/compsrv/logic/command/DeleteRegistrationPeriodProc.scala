package compman.compsrv.logic.command

import cats.Monad
import cats.implicits._
import cats.data.EitherT
import compman.compsrv.logic.CompetitionState
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, DeleteRegistrationPeriodCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.dto.competition.RegistrationPeriodDTO
import compman.compsrv.model.events.payload.{RegistrationGroupDeletedPayload, RegistrationPeriodDeletedPayload}

import scala.jdk.CollectionConverters._

object DeleteRegistrationPeriodProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
      state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = {
    case x @ DeleteRegistrationPeriodCommand(_, _, _) =>
      process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
      command: DeleteRegistrationPeriodCommand,
      state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] =
      for {
        payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
        periodExists = state
          .registrationInfo
          .exists(_.getRegistrationPeriods.containsKey(payload.getPeriodId))
        registrationPeriods = state
          .registrationInfo
          .map(_.getRegistrationPeriods.asScala)
          .getOrElse(Map.empty[String, RegistrationPeriodDTO])

        groupsOfPeriod = state.registrationInfo.map { r =>
          r.getRegistrationPeriods.asScala.values.flatMap(_.getRegistrationGroupIds).toList
        }.getOrElse(List.empty)
        groupsWithoutPeriodDeleted <- EitherT.liftF[F, Errors.Error, List[EventDTO]](
          groupsOfPeriod.filter { gr =>
            !registrationPeriods.exists { case (id, p) => id != payload.getPeriodId && p.getRegistrationGroupIds.contains(gr) }
          }.traverse{
            grId =>
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.REGISTRATION_GROUP_DELETED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(
                  new RegistrationGroupDeletedPayload()
                    .setPeriodId(payload.getPeriodId)
                    .setGroupId(grId)
                )
              )
          }
        )
        event <-
          if (!periodExists) {
            EitherT.fromEither[F](
              Left[Errors.Error, EventDTO](
                Errors.RegistrationPeriodDoesNotExist(payload.getPeriodId)
              )
            )
          } else {
            EitherT.liftF[F, Errors.Error, EventDTO](
              CommandEventOperations[F, EventDTO, EventType].create(
                `type` = EventType.REGISTRATION_PERIOD_DELETED,
                competitorId = None,
                competitionId = command.competitionId,
                categoryId = command.categoryId,
                payload = Some(
                  new RegistrationPeriodDeletedPayload()
                    .setPeriodId(payload.getPeriodId)
                )
              )
            )
          }
      } yield groupsWithoutPeriodDeleted ++ Seq(event)
    eventT.value
  }
}
