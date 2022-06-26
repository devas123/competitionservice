package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.model.command.Commands.GenerateScheduleCommand
import compman.compsrv.model.extensions._
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.GenerateSchedulePayload
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant
import java.util.UUID

class ScheduleServiceSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {

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
  override val initialState: CommandProcessorCompetitionState = CommandProcessorCompetitionState(
    id = competitionId,
    competitors = Utils.groupById(competitors)(_.id),
    competitionProperties = Some(CompetitionProperties().withId(competitionId).withTimeZone("UTC")),
    stages = Map(stageId -> stage, stage2 -> stage.copy().withId(stage2).withCategoryId(category2)),
    fights = Utils.groupById(fights ++ fights2)(_.id),
    categories =
      Map(categoryId -> CategoryDescriptor().withId(categoryId), category2 -> CategoryDescriptor().withId(category2)),
    registrationInfo = None,
    schedule = None
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

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
