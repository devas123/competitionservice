package compman.compsrv.logic.command

import cats.Eval
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.Utils
import compman.compsrv.logic.{assertET, Operations}
import compman.compsrv.logic.fight.FightResultOptionConstants
import compman.compsrv.logic.schedule.StageGraph
import compman.compsrv.model.command.Commands.{CreateFakeCompetitors, GenerateBracketsCommand, SetFightResultCommand}
import compman.compsrv.model.Errors
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.{GenerateBracketsPayload, SetFightResultPayload}
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import compservice.model.protobuf.model.FightStatus.UNCOMPLETABLE
import compservice.model.protobuf.model.StageRoundType.WINNER_BRACKETS
import org.scalatest.{BeforeAndAfter, CancelAfterFailure}
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class SetFightResultSpec extends AnyFunSuite with BeforeAndAfter with TestEntities with CancelAfterFailure {
  import Dependencies._

  override val initialState: CommandProcessorCompetitionState = CommandProcessorCompetitionState(
    id = competitionId,
    competitors = Utils.groupById(competitors)(_.id),
    competitionProperties = None,
    stages = Map(stageId -> stage),
    fights = Utils.groupById(fights)(_.id),
    categories = Map.empty,
    registrationInfo = None,
    schedule = None,
    stageGraph = Some(StageGraph.createStagesDigraph(Seq(stage)))
  )

  val fight: FightDescription = fights.head

  val payload: Option[SetFightResultPayload] = Some(
    SetFightResultPayload().withFightId(fight.id).withStatus(FightStatus.FINISHED)
      .withFightResult(FightResult().withReason("dota2").withResultTypeId("_default_win_points").withWinnerId(
        fight.scores.head.getCompetitorId
      )).withScores(fight.scores)
  )

  val command: SetFightResultCommand =
    SetFightResultCommand(payload = payload, competitionId = Some(competitionId), categoryId = Some(categoryId))

  test("Should set fight result and propagate competitors") {
    val updatedFights = (for { result <- SetFightResultProc[Eval](initialState).apply(command) } yield result).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
  }

  private def isLoserFightUncompletableScore(updatedState: CommandProcessorCompetitionState, s: CompScore) = {
    s.parentFightId.isDefined && s.parentReferenceType.contains(FightReferenceType.LOSER) &&
    updatedState.fights.get(s.parentFightId.get).exists(f =>
      f.status != UNCOMPLETABLE && f.scores.forall(_.competitorId.exists(_.nonEmpty)) && f.roundType == WINNER_BRACKETS
    )
  }

  test("Should propagate competitors to next stage from group stage when have multiple stages") {
    val state = initialState.clearCompetitors.clearFights.clearStages
      .addCategories((categoryId, CategoryDescriptor().withId(categoryId).withName("Test")))
    val createFakeCompetitorsCommand =
      CreateFakeCompetitors(competitionId = Some(competitionId), categoryId = Some(categoryId))

    val executionResult = (for {
      _fff <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      fff  <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      stateWithCompetitors <- EitherT
        .liftF((_fff ++ fff).toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      tmpPreliminaryStageId = UUID.randomUUID().toString
      finalStageId          = UUID.randomUUID().toString
      generateBracketsPayload = GenerateBracketsPayload().addStageDescriptors(
        StageDescriptor().withId(tmpPreliminaryStageId).withName("testPreliminary").withStageType(StageType.PRELIMINARY)
          .withBracketType(BracketType.GROUP).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(0).withWaitForPrevious(false).withHasThirdPlaceFight(false)
          .withStageResultDescriptor(
            StageResultDescriptor().withName("test").withOutputSize(4).withForceManualAssignment(false)
              .withFightResultOptions(FightResultOptionConstants.values)
          )
          .addGroupDescriptors(
            GroupDescriptor()
              .withId(UUID.randomUUID().toString)
              .withName("1")
              .withSize(5),
            GroupDescriptor()
              .withId(UUID.randomUUID().toString)
              .withName("2")
              .withSize(5)
          )
      ).addStageDescriptors(
        StageDescriptor().withId(finalStageId).withName("test").withStageType(StageType.FINAL)
          .withBracketType(BracketType.DOUBLE_ELIMINATION).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(1).withWaitForPrevious(true).withHasThirdPlaceFight(false)
          .withInputDescriptor(StageInputDescriptor().withNumberOfCompetitors(4).addSelectors(
            CompetitorSelector().withApplyToStageId(tmpPreliminaryStageId)
              .withClassifier(SelectorClassifier.FIRST_N_PLACES).addSelectorValue("4")
          )).withStageResultDescriptor(
          StageResultDescriptor().withName("test").withOutputSize(0).withForceManualAssignment(false)
            .withFightResultOptions(FightResultOptionConstants.values.filter(!_.draw))
        )
      )

      generateBracketsCommand =
        GenerateBracketsCommand(Some(generateBracketsPayload), Some(competitionId), Some(categoryId))
      bracketsGenerated <- EitherT(GenerateBracketsProc[Eval](stateWithCompetitors).apply(generateBracketsCommand))
      updatedState <- EitherT
        .liftF(bracketsGenerated.toList.foldM(stateWithCompetitors)((s, e) => Operations.applyEvent[Eval](s, e)))
      preliminaryStageId <- EitherT.fromOption[Eval](
        updatedState.stages.find(_._2.stageType == StageType.PRELIMINARY).map(_._1),
        Errors.InternalError("Cannot find preliminary stage")
      )
      stateWithPropagatedCompetitors <- progressStage[Eval](updatedState, preliminaryStageId, Seq.empty)
      competitorsPropagated <- EitherT.fromOption[Eval](
        stateWithPropagatedCompetitors._2.find(_.`type` == EventType.COMPETITORS_PROPAGATED_TO_STAGE),
        Errors.InternalError("No competitors propagated to stage event").asInstanceOf[Errors.Error]
      )
      _ <- assertET[Eval](
        competitorsPropagated.messageInfo.exists(_.payload.isCompetitorsPropagatedToStagePayload),
        Some("Wrong payload type for competitors propagated event")
      )
      _ <- assertET[Eval](
        competitorsPropagated.messageInfo.flatMap(_.payload.competitorsPropagatedToStagePayload)
          .exists(_.propagations.size == 4),
        Some("Wrong number of propagations")
      )
    } yield competitorsPropagated).value.value
    assert(executionResult.isRight, executionResult)
  }

  test("Should propagate competitors to next stage when have multiple stages") {
    val state = initialState.clearCompetitors.clearFights.clearStages
      .addCategories((categoryId, CategoryDescriptor().withId(categoryId).withName("Test")))
    val createFakeCompetitorsCommand =
      CreateFakeCompetitors(competitionId = Some(competitionId), categoryId = Some(categoryId))

    val executionResult = (for {
      _fff <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      fff  <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      stateWithCompetitors <- EitherT
        .liftF((_fff ++ fff).toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      tmpPreliminaryStageId = UUID.randomUUID().toString
      finalStageId          = UUID.randomUUID().toString
      generateBracketsPayload = GenerateBracketsPayload().addStageDescriptors(
        StageDescriptor().withId(tmpPreliminaryStageId).withName("testPreliminary").withStageType(StageType.PRELIMINARY)
          .withBracketType(BracketType.SINGLE_ELIMINATION).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(0).withWaitForPrevious(false).withHasThirdPlaceFight(false)
          .withStageResultDescriptor(
            StageResultDescriptor().withName("test").withOutputSize(4).withForceManualAssignment(false)
              .withFightResultOptions(FightResultOptionConstants.values.filter(!_.draw))
          )
      ).addStageDescriptors(
        StageDescriptor().withId(finalStageId).withName("test").withStageType(StageType.FINAL)
          .withBracketType(BracketType.DOUBLE_ELIMINATION).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(1).withWaitForPrevious(true).withHasThirdPlaceFight(false)
          .withInputDescriptor(StageInputDescriptor().withNumberOfCompetitors(4).addSelectors(
            CompetitorSelector().withApplyToStageId(tmpPreliminaryStageId)
              .withClassifier(SelectorClassifier.FIRST_N_PLACES).addSelectorValue("4")
          )).withStageResultDescriptor(
            StageResultDescriptor().withName("test").withOutputSize(0).withForceManualAssignment(false)
              .withFightResultOptions(FightResultOptionConstants.values.filter(!_.draw))
          )
      )

      generateBracketsCommand =
        GenerateBracketsCommand(Some(generateBracketsPayload), Some(competitionId), Some(categoryId))
      bracketsGenerated <- EitherT(GenerateBracketsProc[Eval](stateWithCompetitors).apply(generateBracketsCommand))
      updatedState <- EitherT
        .liftF(bracketsGenerated.toList.foldM(stateWithCompetitors)((s, e) => Operations.applyEvent[Eval](s, e)))
      preliminaryStageId <- EitherT.fromOption[Eval](
        updatedState.stages.find(_._2.stageType == StageType.PRELIMINARY).map(_._1),
        Errors.InternalError("Cannot find preliminary stage")
      )
      stateWithPropagatedCompetitors <- progressStage[Eval](updatedState, preliminaryStageId, Seq.empty)
      competitorsPropagated <- EitherT.fromOption[Eval](
        stateWithPropagatedCompetitors._2.find(_.`type` == EventType.COMPETITORS_PROPAGATED_TO_STAGE),
        Errors.InternalError("No competitors propagated to stage event").asInstanceOf[Errors.Error]
      )
      _ <- assertET[Eval](
        competitorsPropagated.messageInfo.exists(_.payload.isCompetitorsPropagatedToStagePayload),
        Some("Wrong payload type for competitors propagated event")
      )
      _ <- assertET[Eval](
        competitorsPropagated.messageInfo.flatMap(_.payload.competitorsPropagatedToStagePayload)
          .exists(_.propagations.size == 4),
        Some("Wrong number of propagations")
      )
    } yield competitorsPropagated).value.value
    assert(executionResult.isRight, executionResult)
  }

  test("Should set fight result and propagate competitors in uncompletable fights for Double elimination") {
    val state = initialState.clearCompetitors.clearFights
      .addCategories((categoryId, CategoryDescriptor().withId(categoryId).withName("Test")))
    val createFakeCompetitorsCommand =
      CreateFakeCompetitors(competitionId = Some(competitionId), categoryId = Some(categoryId))

    val executionResult = (for {
      fff                  <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      stateWithCompetitors <- EitherT.liftF(fff.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      generateBracketsPayload = GenerateBracketsPayload().addStageDescriptors(
        StageDescriptor().withId(UUID.randomUUID().toString).withName("test").withStageType(StageType.FINAL)
          .withBracketType(BracketType.DOUBLE_ELIMINATION).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(0).withWaitForPrevious(false).withHasThirdPlaceFight(false)
          .withStageResultDescriptor(
            StageResultDescriptor().withName("test").withOutputSize(0).withForceManualAssignment(false)
              .withFightResultOptions(FightResultOptionConstants.values.filter(!_.draw))
          )
      )
      generateBracketsCommand =
        GenerateBracketsCommand(Some(generateBracketsPayload), Some(competitionId), Some(categoryId))
      bracketsGenerated <- EitherT(GenerateBracketsProc[Eval](stateWithCompetitors).apply(generateBracketsCommand))
      updatedState <- EitherT.liftF(bracketsGenerated.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      loserFightUncompletable <- EitherT.fromOption[Eval](
        updatedState.fights.values.find(f =>
          f.roundType == StageRoundType.LOSER_BRACKETS && f.status == UNCOMPLETABLE && f.round == 0 &&
            f.scores.exists(s => isLoserFightUncompletableScore(updatedState, s))
        ),
        Errors.InternalError("Cannot find appropriate loser fight")
      )
      parentFightId <- EitherT.fromOption[Eval](
        loserFightUncompletable.scores.find(s => isLoserFightUncompletableScore(updatedState, s))
          .flatMap(_.parentFightId),
        Errors.InternalError("Cannot find parent fight")
      )
      parentFight = updatedState.fights(parentFightId)
      payload = SetFightResultPayload().withFightId(parentFight.id).withStatus(FightStatus.FINISHED)
        .withFightResult(FightResult().withReason("dota2").withResultTypeId("_default_win_points").withWinnerId(
          parentFight.scores.head.getCompetitorId
        )).withScores(parentFight.scores)
      command = SetFightResultCommand(
        payload = Some(payload),
        competitionId = Some(competitionId),
        categoryId = Some(categoryId)
      )
      result <- EitherT(SetFightResultProc[Eval](updatedState).apply(command))
      stateWithFightResultSet <- EitherT
        .liftF(result.toList.foldM(updatedState)((s, e) => Operations.applyEvent[Eval](s, e)))
      lastEvent            = result.last
      propagatedAssignment = lastEvent.messageInfo.get.payload.fightCompetitorsAssignedPayload.get.assignments.last
      updatedFight         = stateWithFightResultSet.fights(propagatedAssignment.toFightId)
      _ <- assertET[Eval](
        result.filter(_.messageInfo.exists(_.payload.isFightCompetitorsAssignedPayload)).forall(_.messageInfo.exists(
          _.payload.fightCompetitorsAssignedPayload.get.assignments.forall(_.competitorId.nonEmpty)
        )), {
          val missingCompetitorIdAssignments = result
            .filter(_.messageInfo.exists(_.payload.isFightCompetitorsAssignedPayload))
            .flatMap(_.messageInfo.get.payload.fightCompetitorsAssignedPayload.get.assignments)
            .filter(_.competitorId.isEmpty)
          Some(s"Not all assignments have competitorId: ${missingCompetitorIdAssignments.mkString("\n")}")
        }
      )
      _ <- assertET[Eval](updatedFight.scores.nonEmpty, Some("Scores are empty"))
      _ <- assertET[Eval](
        updatedFight.scores.exists(_.competitorId.contains(propagatedAssignment.competitorId)),
        Some(s"Competitor is empty: $propagatedAssignment")
      )
      _ <- assertET[Eval](result.size > 2, Some(s"Too few results: $result"))
      last = result.last
      _ <- assertET[Eval](last.messageInfo.isDefined, Some("Message info undefined"))
      _ <- assertET[Eval](last.messageInfo.get.payload.isFightCompetitorsAssignedPayload, Some("Payload is wrong"))
      _ <- assertET[Eval](
        last.messageInfo.get.payload.fightCompetitorsAssignedPayload.get.assignments.size > 1,
        Some(s"Too few assignments in the last assignment: $last")
      )
    } yield result).value.value
    assert(executionResult.isRight, executionResult)
  }


  test("Should set stage result.") {
    val finishedFights = fights.map(f =>
      f.withStatus(FightStatus.FINISHED).withFightResult(
        FightResult().withReason("test").withWinnerId(f.scores.head.getCompetitorId).withResultTypeId("default")
      )
    )
    val updatedFights = (for {
      fight1Update <- SetFightResultProc[Eval](initialState.copy(
        fights = Utils.groupById(finishedFights)(_.id),
        stages = Map(stageId -> singleEliminationBracketsStage)
      )).apply(command)
    } yield fight1Update).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
    val stageResultEvent = events.find(_.`type` == EventType.DASHBOARD_STAGE_RESULT_SET)
    assert(stageResultEvent.isDefined)
  }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
