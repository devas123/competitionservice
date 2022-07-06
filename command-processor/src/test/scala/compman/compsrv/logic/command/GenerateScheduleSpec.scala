package compman.compsrv.logic.command

import cats.Eval
import cats.data.EitherT
import cats.implicits.toFoldableOps
import compman.compsrv.Utils
import compman.compsrv.logic.schedule.StageGraph
import compman.compsrv.logic.{assertET, Operations}
import compman.compsrv.logic.fight.FightResultOptionConstants
import compman.compsrv.model.command.Commands.{CreateFakeCompetitors, GenerateBracketsCommand, GenerateScheduleCommand}
import compman.compsrv.model.extensions._
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.{GenerateBracketsPayload, GenerateSchedulePayload}
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import org.scalatest.{BeforeAndAfter, CancelAfterFailure}
import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant
import java.util.UUID

class GenerateScheduleSpec extends AnyFunSuite with BeforeAndAfter with TestEntities with CancelAfterFailure {

  import Dependencies._

  override val stage: StageDescriptor = new StageDescriptor().withId(stageId).withCategoryId(categoryId)
    .withStageType(StageType.FINAL).withBracketType(BracketType.SINGLE_ELIMINATION).withStageOrder(0)
    .withInputDescriptor(
      StageInputDescriptor().withSelectors(Seq.empty).withDistributionType(DistributionType.AUTOMATIC)
        .withNumberOfCompetitors(competitors.size)
    )

  private val category2 = s"$categoryId-2"
  val payload: Option[GenerateSchedulePayload] = Some(GenerateSchedulePayload().withMats(Seq(testMat)).withPeriods(Seq(
    Period().withId(periodId).withName("period").withStartTime(Instant.now().asTimestamp).withIsActive(false)
      .withRiskPercent(10).withScheduleRequirements(Seq(
        ScheduleRequirement().withId("schedReq1").withCategoryIds(Seq(categoryId)).withMatId(matId)
          .withPeriodId(periodId).withEntryOrder(0).withEntryType(ScheduleRequirementType.CATEGORIES),
        ScheduleRequirement().withId("schedReq2").withCategoryIds(Seq(category2)).withMatId(matId)
          .withPeriodId(periodId).withEntryOrder(1).withEntryType(ScheduleRequirementType.CATEGORIES)
      )).withTimeBetweenFights(1)
  )))

  private val stage2: String = stageId + "2"

  private val fights2: Seq[FightDescription] = fights.map(f =>
    f.copy(id = f.id + "2").withStageId(stage2).withCategoryId(category2)
      .withConnections(f.connections.map(c => c.withFightId(c.fightId + "2"))).withScores(
        Option(f.scores).map(_.map(cs => cs.update(_.parentFightId.setIfDefined(cs.parentFightId.map(_ + "2")))))
          .getOrElse(Seq.empty)
      )
  )

  private val stages: Map[String, StageDescriptor] = Map(
    stageId -> stage,
    stage2 -> stage.copy().withId(stage2).withCategoryId(category2)
  )

  override val initialState: CommandProcessorCompetitionState = CommandProcessorCompetitionState(
    id = competitionId,
    competitors = Utils.groupById(competitors)(_.id),
    competitionProperties = Some(CompetitionProperties().withId(competitionId).withTimeZone("UTC")),
    stages = stages,
    fights = Utils.groupById(fights ++ fights2)(_.id),
    categories =
      Map(categoryId -> CategoryDescriptor().withId(categoryId), category2 -> CategoryDescriptor().withId(category2)),
    registrationInfo = None,
    schedule = None,
    stageGraph = Some(StageGraph.createStagesDigraph(stages.values))
  )

  val command: GenerateScheduleCommand =
    GenerateScheduleCommand(payload = payload, competitionId = Some(competitionId), categoryId = None)

  test("Should generate Schedule") {
    val generatedEvents = (for { result <- GenerateScheduleProc[Eval](initialState).apply(command) } yield result).value
    assert(generatedEvents.isRight)
    val events = generatedEvents.getOrElse(List.empty)
    assert(events.nonEmpty)
    assert(events.head.`type` == EventType.SCHEDULE_GENERATED)
    assert(events.head.messageInfo.flatMap(_.payload.scheduleGeneratedPayload).isDefined)
    val receivedPayload = events.head.messageInfo.flatMap(_.payload.scheduleGeneratedPayload).get
    assert(receivedPayload.schedule.isDefined)
    assert(receivedPayload.getSchedule.mats.length == 1)
    assert(receivedPayload.getSchedule.periods.length == 1)
    assert(receivedPayload.getSchedule.periods.head.scheduleEntries.length == 2)
  }

  test("Should generate schedule when there are multiple stages depending on each other") {
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
              .withFightResultOptions(FightResultOptionConstants.values.filter(!_.draw))
          )
          .addGroupDescriptors(
            GroupDescriptor()
              .withId(UUID.randomUUID().toString)
              .withName("name")
              .withSize(10)
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
      generateSchedulePayload = GenerateSchedulePayload().withMats(Seq(testMat, testMat.withId(UUID.randomUUID().toString).withName("Mat 2").withMatOrder(1))).withPeriods(Seq(
        Period().withId(periodId).withName("period").withStartTime(Instant.now().asTimestamp).withIsActive(true)
          .withRiskPercent(10).addScheduleRequirements(
          ScheduleRequirement().withId("schedReq1").withCategoryIds(Seq(categoryId)).withMatId(matId)
//            .withFightIds(updatedState.fights.keySet.toSeq)
            .withPeriodId(periodId).withEntryOrder(0).withEntryType(ScheduleRequirementType.CATEGORIES),
        ).withTimeBetweenFights(1)
      ))
      scheduleGenerated <- EitherT(GenerateScheduleProc[Eval](updatedState).apply(GenerateScheduleCommand(Some(generateSchedulePayload), Some(competitionId), Some(categoryId))))
    } yield scheduleGenerated).value.value
    assert(executionResult.isRight, executionResult)
  }


  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
