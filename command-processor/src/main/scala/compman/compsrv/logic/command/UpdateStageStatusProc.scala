package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.Utils.groupById
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.FightUtils
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, UpdateStageStatusCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.commandpayload.UpdateStageStatusPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload._
import compservice.model.protobuf.model.{FightStatus, StageStatus}

object UpdateStageStatusProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: UpdateStageStatusCommand => process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: UpdateStageStatusCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    def createStageStatusUpdatedEvent(payload: UpdateStageStatusPayload, stageId: String) = {
      CommandEventOperations[F, Event, EventType].create(
        `type` = EventType.STAGE_STATUS_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.StageStatusUpdatedPayload(
          StageStatusUpdatedPayload().withStageId(stageId).withStatus(payload.status)
        ))
      )
    }

    val eventT: EitherT[F, Errors.Error, Seq[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      _ <- assertETErr[F](state.stages.exists(_.contains(payload.stageId)), Errors.StageDoesNotExist(payload.stageId))
      stageId = payload.stageId
      e = payload.status match {
        case StageStatus.APPROVED | StageStatus.WAITING_FOR_APPROVAL | StageStatus.WAITING_FOR_COMPETITORS =>
          val stageFights = state.fights.map(_.values.filter(_.stageId == stageId)).getOrElse(Iterable.empty)
          val dirtyStageFights = groupById(stageFights.map(sf =>
            if (sf.status == FightStatus.UNCOMPLETABLE) { sf.withStatus(FightStatus.PENDING) }
            else sf
          ))(_.id)
          for {
            markedStageFights <-
              if (payload.status == StageStatus.WAITING_FOR_COMPETITORS) Monad[F].pure(dirtyStageFights)
              else FightUtils.markAndProcessUncompletableFights[F](dirtyStageFights)
            fightsUpdated <- CommandEventOperations[F, Event, EventType].create(
              `type` = EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
              competitorId = None,
              competitionId = command.competitionId,
              categoryId = command.categoryId,
              payload = Some(MessageInfo.Payload.FightEditorChangesAppliedPayload(
                FightEditorChangesAppliedPayload().withUpdates(markedStageFights.values.toSeq)
              ))
            )
            stageUpdated <- createStageStatusUpdatedEvent(payload, stageId)
          } yield List(stageUpdated, fightsUpdated)
        case StageStatus.FINISHED | StageStatus.IN_PROGRESS =>
          for { e <- createStageStatusUpdatedEvent(payload, stageId) } yield List(e)
        case _ => Monad[F].pure(List.empty)
      }
      event <- EitherT.liftF(e)
    } yield event
    eventT.value
  }
}
