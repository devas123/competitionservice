package compman.compsrv.logic.command

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic.{assertET, assertETErr}
import compman.compsrv.logic.Operations.{CommandEventOperations, EventOperations, IdOperations}
import compman.compsrv.logic.fight.{CanFail, FightsService, FightStatusUtils, FightUtils}
import compman.compsrv.logic.fight.CompetitorSelectionUtils.Interpreter
import compman.compsrv.model.Errors
import compman.compsrv.model.command.Commands.{InternalCommandProcessorCommand, SetFightResultCommand}
import compman.compsrv.model.Errors.NoPayloadError
import compman.compsrv.Utils.groupById
import compman.compsrv.model.extensions.FightDescrOps
import compservice.model.protobuf.commandpayload.SetFightResultPayload
import compservice.model.protobuf.common.MessageInfo
import compservice.model.protobuf.event.{Event, EventType}
import compservice.model.protobuf.eventpayload.{CompetitorAssignmentDescriptor, CompetitorsPropagatedToStagePayload, FightCompetitorsAssignedPayload, StageResultSetPayload}
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
      FightStatusUtils.isCompleted(it) ||
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
      stageGraph   <- EitherT.fromOption[F](state.stageGraph, Errors.StageGraphMissing())
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
      nextStage = stageGraph.outgoingConnections.get(stage.id)
      stagesToPropagateFightsTo = nextStage match {
        case Some(value) =>
          // now need to check that all children of this stage are finished
          value.ids.filter { sid =>
            val children = stageGraph.incomingConnections(sid).ids
            val manualAssignmentEnabled = state.stages(sid).inputDescriptor
              .exists(_.selectors.exists(_.classifier == SelectorClassifier.MANUAL))
            children.forall(childId =>
              !manualAssignmentEnabled &&
                checkIfAllStageFightsFinished(state, Some(childId), Option(fightId).map(Set(_)).getOrElse(Set.empty))
            )
          }
        case None => Seq.empty
      }
      stageResultSetEvent <- createStageResultSetEventIfFinished[F](
        command,
        state,
        payload,
        fight,
        stageId,
        stageFights,
        allStageFightsFinished,
        stagesToPropagateFightsTo
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
    stagesToPropagateCompetitorsTo: Seq[String]
  ): EitherT[F, Errors.Error, List[Event]] = {
    def createStageResultSetEvent(
      stage: StageDescriptor,
      fightsWithResult: Map[String, FightDescription],
      fightResultOptions: List[FightResultOption]
    ): EitherT[F, Errors.Error, Event] = for {
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
    } yield stageResultSetEvent

    def createCompetitorsPropagatedEvent(
      stageToSendCompetitorsTo: StageDescriptor,
      stageGraph: DiGraph,
      competitorStageResult: Map[String, Seq[CompetitorStageResult]],
      fights: Map[String, FightDescription]
    ): EitherT[F, Errors.Error, Event] = for {
      competitorsToPropagate <- EitherT.liftF(FightUtils.applyStageInputDescriptorToResultsAndFights[F](
        stageToSendCompetitorsTo,
        stageGraph,
        competitorStageResult,
        fights
      ))
      propagatedCompetitorsSet = competitorsToPropagate.toSet
      propagatedStageFights    = state.fights.values.filter(_.stageId == stageToSendCompetitorsTo.id)
      fightsWIthPropagatedCompetitors <- EitherT(createFightsWithPropagatedCompetitors[F](
        state.competitors.values,
        stageToSendCompetitorsTo.bracketType,
        propagatedCompetitorsSet,
        propagatedStageFights
      ))
      propagations = createPropagationsList(fightsWIthPropagatedCompetitors)
      competitorsPropagatedToStageEvent <- EitherT.liftF(CommandEventOperations[F, Event].create(
        `type` = EventType.COMPETITORS_PROPAGATED_TO_STAGE,
        competitorId = None,
        competitionId = command.competitionId,
        categoryId = command.categoryId,
        payload = Some(MessageInfo.Payload.CompetitorsPropagatedToStagePayload(
          CompetitorsPropagatedToStagePayload().withStageId(stageToSendCompetitorsTo.id).withPropagations(propagations)
        ))
      ))
    } yield competitorsPropagatedToStageEvent

    if (allStageFightsFinished) {
      for {
        stage      <- EitherT.fromOption[F](state.stages.get(stageId), Errors.StageDoesNotExist(stageId))
        stageGraph <- EitherT.fromOption[F](state.stageGraph, Errors.StageGraphMissing())
        fightsWithResult   = stageFights + (fight.id -> fight.withFightResult(payload.getFightResult))
        fightResultOptions = stage.getStageResultDescriptor.fightResultOptions.toList
        result <- createStageResultSetEvent(stage, fightsWithResult, fightResultOptions)
        createdResults <- EitherT.fromOption[F](result.messageInfo.flatMap(_.payload.stageResultSetPayload).map(_.results)
          .map(compResults => compResults.groupBy(_.competitorId)), Errors.StageResultsMissing())
        competitorsPropagatedEvents <- stagesToPropagateCompetitorsTo.toList.traverse { id =>
          val existingResults = getCompetitorResults(stageGraph.incomingConnections(id).ids, state)
          val updated = createdResults.foldLeft(existingResults){
            case (acc, (key, results)) =>
              acc.updatedWith(key)(_.map(seq => seq ++ results).orElse(Some(results)).map(_.distinctBy(_.stageId)))
          }
          createCompetitorsPropagatedEvent(
            state.stages(id),
            stageGraph,
            updated,
            state.fights
          )
        }
      } yield List(result) ++ competitorsPropagatedEvents
    } else { EitherT.rightT[F, Errors.Error](List.empty) }
  }

  def getCompetitorResults(
    stageIds: Seq[String],
    state: CommandProcessorCompetitionState
  ): Map[String, Seq[CompetitorStageResult]] = {
    stageIds.map(state.stages).flatMap(_.stageResultDescriptor.map(_.competitorResults).getOrElse(Seq.empty))
      .groupBy(_.competitorId)
  }

  def createPropagationsList(
    fightsWIthPropagatedCompetitors: List[FightDescription]
  ): Seq[CompetitorAssignmentDescriptor] = {
    fightsWIthPropagatedCompetitors.flatMap(f =>
      f.competitors.map(cid => CompetitorAssignmentDescriptor().withCompetitorId(cid).withToFightId(f.id))
    )
  }

  def createFightsWithPropagatedCompetitors[F[+_]: Monad: IdOperations: EventOperations: Interpreter](
    competitors: Iterable[Competitor],
    bracketType: BracketType,
    propagatedCompetitorsSet: Set[String],
    propagatedStageFights: Iterable[FightDescription]
  ): F[CanFail[List[FightDescription]]] = {
    FightsService.distributeCompetitors[F](
      competitors.filter { it => propagatedCompetitorsSet.contains(it.id) }.toList,
      groupById(propagatedStageFights)(_.id)
    ).apply(bracketType)
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
