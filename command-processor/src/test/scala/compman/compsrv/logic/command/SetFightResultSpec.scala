package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.command.Commands.SetFightResultCommand
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.SetFightResultPayload
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class SetFightResultSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {
  import Dependencies._

  val stage: StageDescriptor = StageDescriptor().withId(stageId)

  val initialState: CompetitionState = CompetitionState(
    id = competitionId,
    competitors = Some(Utils.groupById(competitors)(_.id)),
    competitionProperties = None,
    stages = Some(Map(stageId -> stage)),
    fights = Some(Utils.groupById(fights)(_.id)),
    categories = None,
    registrationInfo = None,
    schedule = None,
    revision = 0
  )

  val fight: FightDescription = fights.head

  val payload: Option[SetFightResultPayload] = Some(
    SetFightResultPayload().withFightId(fight.id).withStatus(FightStatus.FINISHED)
      .withFightResult(FightResult().withReason("dota2").withResultTypeId("_default_win_points")
      .withWinnerId(fight.scores.head.getCompetitorId))
      .withScores(fight.scores)
  )

  val command: SetFightResultCommand =
    SetFightResultCommand(payload = payload, competitionId = Some(competitionId), categoryId = Some(categoryId))

  test("Should set fight result and propagate competitors") {
    val updatedFights =
      (for { result <- SetFightResultProc[Eval](initialState).apply(command) } yield result).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
  }

  test("Should set stage result.") {
    val finishedFights = fights.map(f =>
      f.withStatus(FightStatus.FINISHED)
        .withFightResult(FightResult()
        .withReason("test")
        .withWinnerId(f.scores.head.getCompetitorId)
        .withResultTypeId("default"))
    )
    val updatedFights =
      (for {
        fight1Update <- SetFightResultProc[Eval](initialState
          .copy(
            fights = Some(Utils.groupById(finishedFights)(_.id)),
            stages = Some(Map(stageId -> singleEliminationBracketsStage))
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
