package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.{assertET, assertETErr}
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{FightsService, FightUtils}
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, SetFightResultCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.extensions._
import compservice.model.protobuf.commandpayload.SetFightResultPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{FightCompetitorsAssignedPayload, StageResultSetPayload}
import compservice.model.protobuf.model._

object SetFightResultProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: SetFightResultCommand => process[F](x, state)
  }

  def getIdToProceed(ref: FightReferenceType, fight: FightDescription, payload: SetFightResultPayload): Option[String] =
    ref match {
      case FightReferenceType.WINNER => fight.winnerId
      case FightReferenceType.LOSER | FightReferenceType.PROPAGATED => Option(payload.scores)
          .flatMap(_.find { s => !fight.winnerId.contains(s.getCompetitorId) }).map(_.getCompetitorId)
      case _ => None
    }

  private def checkIfAllStageFightsFinished(
    state: CommandProcessorCompetitionState,
    stageId: Option[String],
    additionalFinishedFightIds: Set[String]
  ) = stageId.exists { sid =>
    state.fights.values.filter(_.stageId == sid).forall { it =>
      List(FightStatus.FINISHED, FightStatus.WALKOVER, FightStatus.UNCOMPLETABLE).contains(it.status) ||
      additionalFinishedFightIds.contains(it.id)
    }
  }

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: SetFightResultCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    val eventT: EitherT[F, Errors.Error, List[Event]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      fightId = payload.fightId
      _ <- assertET[F](payload.fightResult.isDefined, Some("Fight result missing"))
      fr = payload.getFightResult
      _ <- assertET[F](fr.winnerId.isDefined, Some("Winner ID missing"))
      winnerId = fr.getWinnerId
      _ <- assertETErr[F](state.fights.contains(fightId), Errors.FightDoesNotExist(fightId))
      fight       = state.fights(fightId)
      stageId     = fight.stageId
      stageFights = state.fights.filter(_._2.stageId == stageId)
      fightUpdates <- EitherT.liftF(updates[F](command, payload, winnerId, fight, stageFights))
      status = payload.status
      dashboardFightResultSetEvent <- EitherT.liftF(CommandEventOperations[F, Event].create(
        `type` = EventType.DASHBOARD_FIGHT_RESULT_SET,
        competitorId = command.competitorId,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.SetFightResultPayload(payload.withStatus(status)))
      ))
      allStageFightsFinished =
        checkIfAllStageFightsFinished(state, Some(stageId), Option(fightId).map(Set(_)).getOrElse(Set.empty))
      events <-
        if (allStageFightsFinished) {
          val k: EitherT[F, Errors.Error, List[Event]] = for {
            stage <- EitherT.fromOption[F](state.stages.get(stageId), Errors.StageDoesNotExist(stageId))
            fightsWithResult   = stageFights + (fight.id -> fight.withFightResult(payload.getFightResult))
            fightResultOptions = Option(stage.getStageResultDescriptor).map(_.fightResultOptions).map(_.toList)
            stageResults <- EitherT(
              FightsService.buildStageResult[F](
                StageStatus.FINISHED,
                stage.stageType,
                fightsWithResult.values.toList,
                stageId,
                fightResultOptions
              ).apply(stage.bracketType)
            )
            stageResultSetEvent <- EitherT.liftF(CommandEventOperations[F, Event].create(
              `type` = EventType.DASHBOARD_STAGE_RESULT_SET,
              competitorId = command.competitorId,
              competitionId = command.competitionId,
              categoryId = command.categoryId,
              payload = Some(MessageInfo.Payload.StageResultSetPayload(
                StageResultSetPayload().withStageId(stageId).withResults(stageResults)
              ))
            ))
          } yield List(dashboardFightResultSetEvent, stageResultSetEvent)
          k
        } else {
          val k: EitherT[F, Errors.Error, List[Event]] = EitherT
            .rightT[F, Errors.Error](List(dashboardFightResultSetEvent))
          k
        }
    } yield events ++ fightUpdates
    eventT.value
  }

  private def updates[F[+_]: Monad: IdOperations: EventOperations](
    command: SetFightResultCommand,
    payload: SetFightResultPayload,
    winnerId: String,
    fight: FightDescription,
    stageFights: Map[String, FightDescription]
  ) = {
    FightReferenceType.values.toList.foldMapM[F, List[Event]](ref => {
      val k: EitherT[F, Errors.Error, List[Event]] = for {
        id          <- EitherT.fromOption[F](getIdToProceed(ref, fight, payload), Errors.InternalError())
        assignments <- EitherT.liftF(FightUtils.advanceFighterToSiblingFights[F](id, payload.fightId, ref, stageFights))

        events <-
          if (assignments._2.nonEmpty) EitherT.liftF[F, Errors.Error, Event](CommandEventOperations[F, Event].create(
            `type` = EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
            competitorId = command.competitorId,
            competitionId = command.competitionId,
            categoryId = command.categoryId,
            payload = Some(MessageInfo.Payload.FightCompetitorsAssignedPayload(
              FightCompetitorsAssignedPayload().withAssignments(assignments._2)
            ))
          )).map(List(_))
          else EitherT.liftF[F, Errors.Error, List[Event]](Monad[F].pure(List.empty[Event]))
      } yield events
      k.value.map(_.getOrElse(List.empty))
    })
  }
}
