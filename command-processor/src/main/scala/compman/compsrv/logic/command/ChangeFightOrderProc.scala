package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits.toFunctorFilterOps
import compman.compsrv.logic.assertETErr
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.CommonFightUtils
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{ChangeFightOrderCommand, InternalCommandProcessorCommand}
import compman.compsrv.model.Errors.{FightDoesNotExist, NoPayloadError, NoScheduleError}
import compman.compsrv.Utils
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.FightStartTimeUpdatedPayload
import compservice.model.protobuf.model.{CommandProcessorCompetitionState, FightStartTimePair, FightStatus}

object ChangeFightOrderProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x @ ChangeFightOrderCommand(_, _, _) => process(x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: ChangeFightOrderCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption(command.payload, NoPayloadError())
      fightToMove  = state.fights.get(payload.fightId)
      newMatExists = state.schedule.exists(sched => sched.mats.exists(mat => payload.newMatId == mat.id))
      _ <- assertETErr(newMatExists, Errors.MatDoesNotExist(payload.newMatId))
      _ <- assertETErr(
        !fightToMove.exists(f => Seq(FightStatus.IN_PROGRESS, FightStatus.FINISHED).contains(f.status)),
        Errors.FightCannotBeMoved(payload.fightId)
      )
      currentFights = state.fights
      fight    <- EitherT.fromOption(currentFights.get(payload.fightId), FightDoesNotExist(payload.fightId))
      schedule <- EitherT.fromOption(state.schedule, NoScheduleError())
      mats    = Utils.groupById(schedule.mats)(_.id)
      updates = CommonFightUtils.generateUpdates(payload, fight, currentFights)
      newFights = updates.mapFilter { case (_, update) =>
        for {
          f               <- currentFights.get(update.fightId)
          mat             <- mats.get(update.matId)
          startTime       <- update.startTime
          scheduleEntryId <- f.scheduleEntryId
        } yield FightStartTimePair().update(
          _.scheduleEntryId := scheduleEntryId,
          _.startTime       := startTime,
          _.numberOnMat     := update.numberOnMat,
          _.matId           := mat.id,
          _.periodId        := mat.periodId,
          _.fightId         := f.id,
          _.invalid         := f.invalid,
          _.fightCategoryId := f.categoryId
        )
      }
      fightStartTimeUpdatedPayload = FightStartTimeUpdatedPayload().withNewFights(newFights)
      event <- EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
        `type` = EventType.FIGHTS_START_TIME_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.FightStartTimeUpdatedPayload(fightStartTimeUpdatedPayload))
      ))
    } yield Seq(event)
    eventT.value
  }
}
