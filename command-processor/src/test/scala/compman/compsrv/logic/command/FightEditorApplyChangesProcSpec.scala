package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.command.Commands.FightEditorApplyChangesCommand
import compman.compsrv.model.commands.payload.{FightEditorApplyChangesPayload, FightsCompetitorUpdated}
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload
import compman.compsrv.model.Payload
import compman.compsrv.service.TestEntities
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class FightEditorApplyChangesProcSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {
  import Dependencies._


  val stage: StageDescriptorDTO = new StageDescriptorDTO().setId(stageId)

  val initialState: CompetitionState = CompetitionState(
    id = competitionId,
    competitors = Some(
      Utils.groupById(competitors)(_.getId)
    ),
    competitionProperties = None,
    stages = Some(Map(stageId -> stage)),
    fights = None,
    categories = None,
    registrationInfo = None,
    schedule = None,
    revision = 0
  )

  val payload: Option[FightEditorApplyChangesPayload] =
    Some(new FightEditorApplyChangesPayload().setStageId(stageId).setBracketsChanges(Array(
      new FightsCompetitorUpdated().setFightId("fight1").setCompetitors(Array("competitor1")),
      new FightsCompetitorUpdated().setFightId("fight2").setCompetitors(Array("competitor2", "competitor3"))
    )))

  val command: FightEditorApplyChangesCommand = FightEditorApplyChangesCommand(
    payload = payload,
    competitionId = Some(competitionId),
    categoryId = Some(categoryId)
  )

  test("Should set fight statuses") {
    val updatedFights = (for {
      newFights <- Eval.later(Utils.groupById(fights)(_.getId))
      result <- FightEditorApplyChangesProc[Eval, Payload](initialState.copy(fights = Some(newFights)))
        .apply(command)
    } yield result).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
    assert(events.head.getType == EventType.FIGHTS_EDITOR_CHANGE_APPLIED)
    assert(events.head.getPayload != null)
    assert(events.head.getPayload.isInstanceOf[FightEditorChangesAppliedPayload])
    val eventPayload = events.head.getPayload.asInstanceOf[FightEditorChangesAppliedPayload]
    assert(eventPayload.getUpdates != null)
    assert(eventPayload.getUpdates.find(_.getId == "fight1").map(_.getStatus).contains(FightStatus.UNCOMPLETABLE))
    assert(eventPayload.getUpdates.find(_.getId == "fight2").map(_.getStatus).contains(FightStatus.PENDING))
  }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
