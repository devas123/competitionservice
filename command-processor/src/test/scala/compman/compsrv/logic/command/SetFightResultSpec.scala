package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.command.Commands.SetFightResultCommand
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.Payload
import compman.compsrv.model.events.EventType
import compman.compsrv.service.TestEntities
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class SetFightResultSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {
  import Dependencies._

  val stage: StageDescriptorDTO = new StageDescriptorDTO().setId(stageId)

  val initialState: CompetitionState = CompetitionState(
    id = competitionId,
    competitors = Some(Utils.groupById(competitors)(_.getId)),
    competitionProperties = None,
    stages = Some(Map(stageId -> stage)),
    fights = Some(Utils.groupById(fights)(_.getId)),
    categories = None,
    registrationInfo = None,
    schedule = None,
    revision = 0
  )

  val fight: FightDescriptionDTO = fights.head

  val payload: Option[SetFightResultPayload] = Some(
    new SetFightResultPayload().setFightId(fight.getId).setStatus(FightStatus.FINISHED)
      .setFightResult(new FightResultDTO().setReason("dota2").setResultTypeId("_default_win_points")
      .setWinnerId(fight.getScores.head.getCompetitorId))
      .setScores(fight.getScores)
  )

  val command: SetFightResultCommand =
    SetFightResultCommand(payload = payload, competitionId = Some(competitionId), categoryId = Some(categoryId))

  test("Should set fight result and propagate competitors") {
    val updatedFights =
      (for { result <- SetFightResultProc[Eval, Payload](initialState).apply(command) } yield result).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
  }

  test("Should set stage result.") {
    val finishedFights = fights.map(f =>
      f.setStatus(FightStatus.FINISHED)
        .setFightResult(new FightResultDTO()
        .setReason("test")
        .setWinnerId(f.getScores.head.getCompetitorId)
        .setResultTypeId("default"))
    )
    val updatedFights =
      (for {
        fight1Update <- SetFightResultProc[Eval, Payload](initialState
          .copy(
            fights = Some(Utils.groupById(finishedFights)(_.getId)),
            stages = Some(Map(stageId -> singleEliminationBracketsStage))
          )).apply(command)
        } yield fight1Update).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
    val stageResultEvent = events.find(_.getType == EventType.DASHBOARD_STAGE_RESULT_SET)
    assert(stageResultEvent.isDefined)
  }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
