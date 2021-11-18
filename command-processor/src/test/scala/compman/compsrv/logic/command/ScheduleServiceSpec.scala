package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.command.Commands.GenerateScheduleCommand
import compman.compsrv.model.commands.payload.GenerateSchedulePayload
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleRequirementDTO, ScheduleRequirementType}
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ScheduleGeneratedPayload
import compman.compsrv.model.Payload
import compman.compsrv.service.TestEntities
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant
import java.util.UUID

class ScheduleServiceSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {

  import Dependencies._

  val stage: StageDescriptorDTO = new StageDescriptorDTO().setId(stageId).setCategoryId(categoryId)
    .setStageType(StageType.FINAL).setBracketType(BracketType.SINGLE_ELIMINATION).setStageOrder(0).setInputDescriptor(
      new StageInputDescriptorDTO().setId(stageId).setSelectors(Array.empty)
        .setDistributionType(DistributionType.AUTOMATIC).setNumberOfCompetitors(competitors.size)
    )

  private val category2 = s"$categoryId-2"
  val payload: Option[GenerateSchedulePayload] = Some(
    new GenerateSchedulePayload().setMats(Array(testMat)).setPeriods(Array(
      new PeriodDTO().setId(periodId).setName("period").setStartTime(Instant.now()).setIsActive(false)
        .setRiskPercent(BigDecimal(10).bigDecimal).setScheduleRequirements(Array(
          new ScheduleRequirementDTO().setId("schedReq1").setCategoryIds(Array(categoryId)).setMatId(matId)
            .setPeriodId(periodId).setEntryOrder(0).setEntryType(ScheduleRequirementType.CATEGORIES),
          new ScheduleRequirementDTO().setId("schedReq2").setCategoryIds(Array(category2)).setMatId(matId)
            .setPeriodId(periodId).setEntryOrder(1).setEntryType(ScheduleRequirementType.CATEGORIES)
        )).setTimeBetweenFights(1)
    ))
  )

  private val stage2: String = stageId + "2"
  import compman.compsrv.model.extensions._

  private val fights2: List[FightDescriptionDTO] = fights.map(f =>
    f.copy(id = f.getId + "2").setStageId(stage2).setCategoryId(category2)
      .setWinFight(Option(f.getWinFight).map(_ + "2").orNull).setScores(
        Option(f.getScores).map(_.map(cs => cs.setParentFightId(Option(cs.getParentFightId).map(_ + "2").orNull)))
          .orNull
      )
  )
  val initialState: CompetitionState = CompetitionState(
    id = competitionId,
    competitors = Some(competitors.groupMapReduce(_.getId)(identity)((a, _) => a)),
    competitionProperties = Some(new CompetitionPropertiesDTO().setId(competitionId).setTimeZone("UTC")),
    stages = Some(Map(stageId -> stage, stage2 -> stage.copy().setId(stage2).setCategoryId(category2))),
    fights = Some(fights ++ fights2).map(_.groupMapReduce(_.getId)(identity)((a, _) => a)),
    categories = Some(Map(
      categoryId -> new CategoryDescriptorDTO().copy().setId(categoryId),
      category2  -> new CategoryDescriptorDTO().copy().setId(category2)
    )),
    registrationInfo = None,
    schedule = None,
    revision = 0
  )

  val command: GenerateScheduleCommand =
    GenerateScheduleCommand(payload = payload, competitionId = Some(competitionId), categoryId = None)

  test("Should generate Schedule") {
    val generatedEvents =
      (for { result <- GenerateScheduleProc[Eval, Payload](initialState).apply(command) } yield result).value
    assert(generatedEvents.isRight)
    val events = generatedEvents.getOrElse(List.empty)
    assert(events.nonEmpty)
    assert(events.head.getType == EventType.SCHEDULE_GENERATED)
    assert(events.head.getPayload != null)
    assert(events.head.getPayload.isInstanceOf[ScheduleGeneratedPayload])
    val receivedPayload = events.head.getPayload.asInstanceOf[ScheduleGeneratedPayload]
    assert(receivedPayload.getSchedule != null)
    assert(receivedPayload.getSchedule.getMats.length == 1)
    assert(receivedPayload.getSchedule.getPeriods.length == 1)
    assert(receivedPayload.getSchedule.getPeriods.head.getScheduleEntries.length == 2)
  }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
