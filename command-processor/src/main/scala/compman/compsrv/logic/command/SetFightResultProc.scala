package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{FightsService, FightUtils}
import compman.compsrv.model.{Errors, Payload}
import compman.compsrv.model.command.Commands.{Command, SetFightResultCommand}
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.{FightCompetitorsAssignedPayload, StageResultSetPayload}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.{FightReferenceType, StageStatus}
import compman.compsrv.model.dto.competition.{FightDescriptionDTO, FightStatus}
import compman.compsrv.model.extensions.FightDescrOps

object SetFightResultProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations, P <: Payload](
    state: CompetitionState
  ): PartialFunction[Command[P], F[Either[Errors.Error, Seq[EventDTO]]]] = { case x: SetFightResultCommand =>
    process[F](x, state)
  }

  def getIdToProceed(
    ref: FightReferenceType,
    fight: FightDescriptionDTO,
    payload: SetFightResultPayload
  ): Option[String] = ref match {
    case FightReferenceType.WINNER => fight.winnerId
    case FightReferenceType.LOSER | FightReferenceType.PROPAGATED => Option(payload.getScores)
        .flatMap(_.find { s => !fight.winnerId.contains(s.getCompetitorId) }).map(_.getCompetitorId)
  }

  private def checkIfAllStageFightsFinished(
    state: CompetitionState,
    stageId: Option[String],
    additionalFinishedFightIds: Set[String]
  ) = stageId.flatMap { sid =>
    state.fights.map(_.values.filter(_.getStageId == sid)).map(_.forall { it =>
      List(FightStatus.FINISHED, FightStatus.WALKOVER, FightStatus.UNCOMPLETABLE).contains(it.getStatus) ||
      additionalFinishedFightIds.contains(it.getId)
    })
  }.getOrElse(false)

  private def process[F[+_]: Monad: IdOperations: EventOperations](
    command: SetFightResultCommand,
    state: CompetitionState
  ): F[Either[Errors.Error, Seq[EventDTO]]] = {
    val eventT: EitherT[F, Errors.Error, List[EventDTO]] = for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      fightId = payload.getFightId
      fr      = payload.getFightResult
      _ <- assertET[F](fr != null, Some("Fight result missing"))
      winnerId = fr.getWinnerId
      _ <- assertETErr[F](state.fights.exists(_.contains(fightId)), Errors.FightDoesNotExist(fightId))
      fight   = state.fights.flatMap(_.get(fightId)).get
      stageId = fight.getStageId
      stageFights <- EitherT
        .fromOption[F](state.fights.map(_.filter(_._2.getStageId == stageId)), Errors.InternalError())
      fightUpdates <- EitherT.liftF(updates[F](command, payload, winnerId, fight, stageFights))
      dashboardFightResultSetEvent <- EitherT.liftF(CommandEventOperations[F, EventDTO, EventType].create(
        `type` = EventType.DASHBOARD_FIGHT_RESULT_SET,
        competitorId = command.competitorId,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(payload)
      ))
      allStageFightsFinished =
        checkIfAllStageFightsFinished(state, Some(stageId), Option(fightId).map(Set(_)).getOrElse(Set.empty))
      events <-
        if (allStageFightsFinished) {
          val k: EitherT[F, Errors.Error, List[EventDTO]] = for {
            stage <- EitherT.fromOption[F](state.stages.flatMap(_.get(stageId)), Errors.StageDoesNotExist(stageId))
            fightsWithResult   = stageFights + (fight.getId -> fight.copy(fightResult = payload.getFightResult))
            fightResultOptions = Option(stage.getStageResultDescriptor).map(_.getFightResultOptions).map(_.toList)
            stageResults <- EitherT(
              FightsService.buildStageResult[F](
                StageStatus.FINISHED,
                stage.getStageType,
                fightsWithResult.values.toList,
                stageId,
                fightResultOptions
              ).apply(stage.getBracketType)
            )
            stageResultSetEvent <- EitherT.liftF(CommandEventOperations[F, EventDTO, EventType].create(
              `type` = EventType.DASHBOARD_STAGE_RESULT_SET,
              competitorId = command.competitorId,
              competitionId = command.competitionId,
              categoryId = command.categoryId,
              payload = Some(new StageResultSetPayload().setStageId(stageId).setResults(stageResults.toArray))
            ))
          } yield List(dashboardFightResultSetEvent, stageResultSetEvent)
          k
        } else {
          val k: EitherT[F, Errors.Error, List[EventDTO]] = EitherT
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
    fight: FightDescriptionDTO,
    stageFights: Map[String, FightDescriptionDTO]
  ) = {
    FightReferenceType.values().toList.foldMapM[F, List[EventDTO]](ref => {
      val k: EitherT[F, Errors.Error, List[EventDTO]] = for {
        _  <- assertET[F](winnerId != null, Some("Winner ID missing"))
        id <- EitherT.fromOption[F](getIdToProceed(ref, fight, payload), Errors.InternalError())
        assignments <- EitherT
          .liftF(FightUtils.advanceFighterToSiblingFights[F](id, payload.getFightId, ref, stageFights))
        events <- EitherT.liftF(CommandEventOperations[F, EventDTO, EventType].create(
          `type` = EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
          competitorId = command.competitorId,
          competitionId = command.competitionId,
          categoryId = command.categoryId,
          payload = Some(new FightCompetitorsAssignedPayload().setAssignments(assignments._2.toArray))
        ))
      } yield List(events)
      k.value.map(_.getOrElse(List.empty))
    })
  }
}
