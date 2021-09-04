package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fights.FightUtils
import compman.compsrv.model.{CompetitionState, Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, UpdateStageStatusCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.{FightEditorChangesAppliedPayload, StageStatusUpdatedPayload}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.commands.payload.UpdateStageStatusPayload
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.FightStatus

object UpdateStageStatusProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: UpdateStageStatusCommand =>
    process[F](x, state)
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: UpdateStageStatusCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    def createStageStatusUpdatedEvent(payload: UpdateStageStatusPayload, stageId: String) = {
      CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.STAGE_STATUS_UPDATED,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(new StageStatusUpdatedPayload().setStageId(stageId).setStatus(payload.getStatus))
      )
    }

    val eventT: EitherT[F, Errors.Error, Seq[EventDTO]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      _ <- assertETErr[F](
        state.stages.exists(_.contains(payload.getStageId)),
        Errors.StageDoesNotExist(payload.getStageId)
      )
      stageId = payload.getStageId
      e = payload.getStatus match {
        case StageStatus.APPROVED | StageStatus.WAITING_FOR_APPROVAL | StageStatus.WAITING_FOR_COMPETITORS =>
          val stageFights = state.fights.map(_.values.filter(_.getStageId == stageId)).getOrElse(Iterable.empty)
          val dirtyStageFights = stageFights.map(sf =>
            if (sf.getStatus == FightStatus.UNCOMPLETABLE) { sf.setStatus(FightStatus.PENDING) }
            else sf
          ).groupMapReduce(_.getId)(identity)((a, _) => a)
          for {
            markedStageFights <-
              if (payload.getStatus == StageStatus.WAITING_FOR_COMPETITORS) Monad[F].pure(dirtyStageFights)
              else FightUtils.markAndProcessUncompletableFights[F](dirtyStageFights)
            fightsUpdated <- CommandEventOperations[F, EventDTO, EventType].create(
              `type` = EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
              competitorId = None,
              competitionId = command.competitionId,
              categoryId = command.categoryId,
              payload = Some(new FightEditorChangesAppliedPayload().setUpdates(markedStageFights.values.toArray))
            )
            stageUpdated <- createStageStatusUpdatedEvent(payload, stageId)
          } yield List(stageUpdated, fightsUpdated)
        case StageStatus.FINISHED | StageStatus.IN_PROGRESS =>
          for { e <- createStageStatusUpdatedEvent(payload, stageId) } yield List(e)
      }
      event <- EitherT.liftF(e)
    } yield event
    eventT.value
  }
}
