package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.{assertET, assertETErr}
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{FightsService, FightUtils}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, SetFightResultCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compservice.model.protobuf.commandpayload.SetFightResultPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{FightCompetitorsAssignedPayload, StageResultSetPayload}
import compservice.model.protobuf.model._

object SetFightResultProc {
  def apply[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    state: CommandProcessorCompetitionState
  ): PartialFunction[InternalCommandProcessorCommand[Any], F[Either[Errors.Error, Seq[Event]]]] = {
    case x: SetFightResultCommand => process[F](x, state)
  }

  def getIdToProceed(ref: FightReferenceType, fight: FightDescription, payload: SetFightResultPayload): Option[String] =
    ref match {
      case FightReferenceType.WINNER => payload.fightResult.flatMap(_.winnerId).filter(_.nonEmpty)
      case FightReferenceType.LOSER => fight.scores.find { s =>
          !payload.fightResult.flatMap(_.winnerId).contains(s.getCompetitorId)
        }.map(_.getCompetitorId).filter(_.nonEmpty)
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

  private def process[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: SetFightResultCommand,
    state: CommandProcessorCompetitionState
  ): F[Either[Errors.Error, Seq[Event]]] = {
    for {
      payload <- EitherT.fromOption[F](command.payload, NoPayloadError())
      fightId = payload.fightId
      _ <- assertET[F](payload.fightResult.isDefined, Some("Fight result missing"))
      fr = payload.getFightResult
      _ <- assertET[F](fr.winnerId.isDefined, Some("Winner ID missing"))
      _ <- assertETErr[F](state.fights.contains(fightId), Errors.FightDoesNotExist(fightId))
      fight       = state.fights(fightId)
      stageId     = fight.stageId
      stageFights = state.fights.filter(_._2.stageId == stageId)
      stage        <- EitherT.fromOption[F](state.stages.get(stageId), Errors.StageDoesNotExist(stageId))
      fightUpdates <- EitherT.liftF(createFightUpdates[F](command, payload, fight, stageFights))
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
      manualAssignmentEnabled = stage.inputDescriptor
        .exists(_.selectors.exists(_.classifier == SelectorClassifier.MANUAL))
      stageResultSetEvent <- createStageResultSetEventIfFinished[F](
        command,
        state,
        payload,
        fight,
        stageId,
        stageFights,
        allStageFightsFinished,
        manualAssignmentEnabled
      )
    } yield dashboardFightResultSetEvent +: (stageResultSetEvent ++ fightUpdates)
  }.value

  private def createStageResultSetEventIfFinished[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    command: SetFightResultCommand,
    state: CommandProcessorCompetitionState,
    payload: SetFightResultPayload,
    fight: FightDescription,
    stageId: String,
    stageFights: Map[String, FightDescription],
    allStageFightsFinished: Boolean,
    manualAssignmentEnabled: Boolean
  ) = {
    def createStageResultSetEvent(stage: StageDescriptor, fightsWithResult: Map[String, FightDescription], fightResultOptions: List[FightResultOption]): EitherT[F, Errors.Error, List[Event]] = for {
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
    } yield List(stageResultSetEvent)

//    def createCompetitorsPropagatedEvent = for {
//      competitorsToPropagate <- FightUtils.applyStageInputDescriptorToResultsAndFights[F]
//    } yield ()

    if (allStageFightsFinished) { for {
      stage <- EitherT.fromOption[F](state.stages.get(stageId), Errors.StageDoesNotExist(stageId))
      fightsWithResult   = stageFights + (fight.id -> fight.withFightResult(payload.getFightResult))
      fightResultOptions = stage.getStageResultDescriptor.fightResultOptions.toList
      result <- createStageResultSetEvent(stage, fightsWithResult, fightResultOptions)
    } yield result }
    else { EitherT.rightT[F, Errors.Error](List.empty) }
  }

  private def createFightUpdates[F[+_]: Monad: IdOperations: EventOperations](
    command: SetFightResultCommand,
    payload: SetFightResultPayload,
    fight: FightDescription,
    stageFights: Map[String, FightDescription]
  ) = {
    def createCompetitorsAssignedEvent(ref: FightReferenceType.Recognized): EitherT[F, Errors.Error, List[Event]] =
      for {
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

    FightReferenceType.values.filter(_ != FightReferenceType.PROPAGATED).toList
      .foldMapM[F, List[Event]](ref => createCompetitorsAssignedEvent(ref).value.map(_.getOrElse(List.empty)))
  }
}
