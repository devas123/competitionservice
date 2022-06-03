package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.model.command.Commands.FightEditorApplyChangesCommand
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.{CompetitorsOfFightUpdated, FightEditorApplyChangesPayload}
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class FightEditorApplyChangesProcSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {
  import Dependencies._

  val payload: Option[FightEditorApplyChangesPayload] =
    Some(FightEditorApplyChangesPayload().withStageId(stageId).withBracketsChanges(Seq(
      CompetitorsOfFightUpdated().withFightId("fight1").withCompetitors(Seq("competitor1")),
      CompetitorsOfFightUpdated().withFightId("fight2").withCompetitors(Seq("competitor2", "competitor3"))
    )))

  val command: FightEditorApplyChangesCommand = FightEditorApplyChangesCommand(
    payload = payload,
    competitionId = Some(competitionId),
    categoryId = Some(categoryId)
  )

  test("Should set fight statuses") {
    val updatedFights = (for {
      newFights <- Eval.later(Utils.groupById(fights)(_.id))
      result    <- FightEditorApplyChangesProc[Eval](initialState.copy(fights = newFights)).apply(command)
    } yield result).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
    assert(events.head.`type` == EventType.FIGHTS_EDITOR_CHANGE_APPLIED)
    assert(events.head.messageInfo.flatMap(_.payload.fightEditorChangesAppliedPayload).isDefined)
    val eventPayload = events.head.messageInfo.flatMap(_.payload.fightEditorChangesAppliedPayload).get
    assert(eventPayload.updates.nonEmpty)
    assert(eventPayload.updates.find(_.id == "fight1").map(_.status).contains(FightStatus.UNCOMPLETABLE))
    assert(eventPayload.updates.find(_.id == "fight2").map(_.status).contains(FightStatus.PENDING))
  }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
