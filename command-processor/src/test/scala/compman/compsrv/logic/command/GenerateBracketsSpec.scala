package compman.compsrv.logic.command

import cats.Eval
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.Utils
import compman.compsrv.logic.{assertET, Operations}
import compman.compsrv.model.command.Commands.{CreateFakeCompetitors, GenerateBracketsCommand}
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.GenerateBracketsPayload
import compservice.model.protobuf.model._
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class GenerateBracketsSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {
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
    val state = initialState.clearCompetitors.clearFights
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
          )
      )

      generateBracketsCommand =
        GenerateBracketsCommand(Some(generateBracketsPayload), Some(competitionId), Some(categoryId))
      bracketsGenerated <- EitherT(GenerateBracketsProc[Eval](stateWithCompetitors).apply(generateBracketsCommand))
      updatedState <- EitherT.liftF(bracketsGenerated.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      _            <- assertET[Eval](updatedState.fights.nonEmpty, Some("Fights are empty"))
      _            <- assertET[Eval](updatedState.stages.nonEmpty, Some("Stages are empty"))
      _ <- assertET[Eval](updatedState.stageGraph.exists(gr =>
        updatedState.stages.keySet
          .forall(stageId => gr.incomingConnections.contains(stageId) && gr.outgoingConnections.contains(stageId))
      ),
        Some("Not all stages are present in stage graph")
      )
      _ <- assertET[Eval](updatedState.categoryIdToFightsIndex.contains(categoryId),
        Some("Category not present in categoryIdToFightsIndex"))
      _ <- assertET[Eval](updatedState.stages.filter(_._2.categoryId == categoryId).keys.forall(
        updatedState.categoryIdToFightsIndex(categoryId).stageIdToFightsGraph.contains
      ),
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

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
