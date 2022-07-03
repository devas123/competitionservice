package compman.compsrv.logic.command

import cats.Eval
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.Utils
import compman.compsrv.logic.{assertET, Operations}
import compman.compsrv.logic.fight.FightResultOptionConstants
import compman.compsrv.model.command.Commands.{CreateFakeCompetitors, GenerateBracketsCommand}
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.GenerateBracketsPayload
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import org.scalatest.{BeforeAndAfter, CancelAfterFailure}
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class GenerateBracketsSpec extends AnyFunSuite with BeforeAndAfter with TestEntities with CancelAfterFailure {
  import Dependencies._

  override val initialState: CommandProcessorCompetitionState = CommandProcessorCompetitionState(
    id = competitionId,
    competitors = Utils.groupById(competitors)(_.id),
    competitionProperties = None,
    fights = Utils.groupById(fights)(_.id),
    categories = Map.empty,
    registrationInfo = None,
    schedule = None
  )

  val fight: FightDescription = fights.head

  test("Should populate stage graph and fights graph correctly") {
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
      updatedState <- EitherT.liftF(bracketsGenerated.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      _            <- assertET[Eval](updatedState.fights.nonEmpty, Some("Fights are empty"))
      _            <- assertET[Eval](updatedState.stages.nonEmpty, Some("Stages are empty"))
      _ <- assertET[Eval](
        updatedState.stageGraph.exists(gr =>
          updatedState.stages.keySet
            .forall(stageId => gr.incomingConnections.contains(stageId) && gr.outgoingConnections.contains(stageId))
        ),
        Some("Not all stages are present in stage graph")
      )
      _ <- assertET[Eval](
        updatedState.categoryIdToFightsIndex.contains(categoryId),
        Some("Category not present in categoryIdToFightsIndex")
      )
      _ <- assertET[Eval](
        updatedState.stages.filter(_._2.categoryId == categoryId).keys
          .forall(updatedState.categoryIdToFightsIndex(categoryId).stageIdToFightsGraph.contains),
        Some("Not all stages present in categoryIdToFightsIndex")
      )
      _ <- assertET[Eval](
        updatedState.stages.keySet.forall { stageId =>
          val stageFights = updatedState.fights.values.filter(_.stageId == stageId)
          val graph       = updatedState.categoryIdToFightsIndex(categoryId).stageIdToFightsGraph(stageId)
          stageFights
            .forall(sf => graph.incomingConnections.contains(sf.id) && graph.outgoingConnections.contains(sf.id))
        },
        Some("Fights graph does not contain all fights")
      )
    } yield ()).value.value
    assert(executionResult.isRight, executionResult)
  }

  test("Should populate stage graph and fights graph correctly for group") {
    val state = initialState.clearCompetitors.clearFights.clearStages
      .addCategories((categoryId, CategoryDescriptor().withId(categoryId).withName("Test")))
    val createFakeCompetitorsCommand =
      CreateFakeCompetitors(competitionId = Some(competitionId), categoryId = Some(categoryId))

    val executionResult = (for {
      _fff <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      fff  <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      stateWithCompetitors <- EitherT
        .liftF((fff ++ _fff).toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      tmpPreliminaryStageId = UUID.randomUUID().toString
      generateBracketsPayload = GenerateBracketsPayload().addStageDescriptors(
        StageDescriptor().withId(tmpPreliminaryStageId).withName("testPreliminary").withStageType(StageType.FINAL)
          .withBracketType(BracketType.GROUP).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(0).withWaitForPrevious(false).withHasThirdPlaceFight(false)
          .withStageResultDescriptor(StageResultDescriptor().withOutputSize(0).withFightResultOptions(
            FightResultOptionConstants.values
          )).addGroupDescriptors(GroupDescriptor().withId(UUID.randomUUID().toString).withName("test").withSize(5))
          .addGroupDescriptors(GroupDescriptor().withId(UUID.randomUUID().toString).withName("test2").withSize(5))
      )
      generateBracketsCommand =
        GenerateBracketsCommand(Some(generateBracketsPayload), Some(competitionId), Some(categoryId))
      bracketsGenerated <- EitherT(GenerateBracketsProc[Eval](stateWithCompetitors).apply(generateBracketsCommand))
      updatedState <- EitherT.liftF(bracketsGenerated.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      _            <- assertET[Eval](updatedState.fights.nonEmpty, Some("Fights are empty"))
      _            <- assertET[Eval](updatedState.stages.nonEmpty, Some("Stages are empty"))
      _ <- assertET[Eval](updatedState.stages.size == 1, Some(s"Stages size is not 1 but ${updatedState.stages.size}"))
      _ <- assertET[Eval](
        updatedState.stageGraph.exists(gr =>
          updatedState.stages.keySet
            .forall(stageId => gr.incomingConnections.contains(stageId) && gr.outgoingConnections.contains(stageId))
        ),
        Some("Not all stages are present in stage graph")
      )
      _ <- assertET[Eval](
        updatedState.categoryIdToFightsIndex.contains(categoryId),
        Some("Category not present in categoryIdToFightsIndex")
      )
      _ <- assertET[Eval](
        updatedState.stages.filter(_._2.categoryId == categoryId).keys
          .forall(updatedState.categoryIdToFightsIndex(categoryId).stageIdToFightsGraph.contains),
        Some("Not all stages present in categoryIdToFightsIndex")
      )
      _ <- assertET[Eval](
        updatedState.stages.keySet.forall { stageId =>
          val stageFights = updatedState.fights.values.filter(_.stageId == stageId)
          val graph       = updatedState.categoryIdToFightsIndex(categoryId).stageIdToFightsGraph(stageId)
          stageFights
            .forall(sf => graph.incomingConnections.contains(sf.id) && graph.outgoingConnections.contains(sf.id))
        },
        Some("Fights graph does not contain all fights")
      )
      stateWithResults <- progressStage[Eval](updatedState, updatedState.stages.keys.head, Seq.empty)
      _ <- assertET[Eval](
        stateWithResults._2.exists(_.`type` == EventType.DASHBOARD_STAGE_RESULT_SET),
        Some("Stage result event missing")
      )
      _ <- assertET[Eval](
        stateWithResults._1.stages.head._2.stageResultDescriptor.exists(_.competitorResults.nonEmpty),
        Some("Stage result not set")
      )
      competitorResults = stateWithResults._1.stages.head._2.stageResultDescriptor.map(_.competitorResults).get
      _ <- assertET[Eval](
        state.competitors.keySet.forall(competitorId => competitorResults.exists(_.competitorId == competitorId)),
        Some(
          s"Not all competitors have results: ${state.competitors.keySet} vs ${competitorResults.map(_.competitorId)}"
        )
      )
    } yield ()).value.value
    assert(executionResult.isRight, executionResult)
  }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
