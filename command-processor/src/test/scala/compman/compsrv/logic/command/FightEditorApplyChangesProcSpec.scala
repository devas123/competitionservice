package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.model.{CompetitionStateImpl, Errors, Payload}
import compman.compsrv.model.command.Commands.FightEditorApplyChangesCommand
import compman.compsrv.model.commands.payload.{FightEditorApplyChangesPayload, FightsCompetitorUpdated}
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.events.{EventDTO, EventType}
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload
import compman.compsrv.service.TestEntities
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class FightEditorApplyChangesProcSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {

  object Dependencies {
    implicit val idOps: IdOperations[Eval] = new IdOperations[Eval] {
      override def generateIdIfMissing(id: Option[String]): Eval[String] = uid

      override def uid: Eval[String] = Eval.later(UUID.randomUUID().toString)

      override def fightId(stageId: String, groupId: String): Eval[String] = uid

      override def competitorId(competitor: CompetitorDTO): Eval[String] = uid

      override def categoryId(category: CategoryDescriptorDTO): Eval[String] = uid

      override def registrationPeriodId(period: RegistrationPeriodDTO): Eval[String] = uid

      override def registrationGroupId(group: RegistrationGroupDTO): Eval[String] = uid
    }

    implicit val eventOps: EventOperations[Eval] = new EventOperations[Eval] {
      override def lift(obj: => Seq[EventDTO]): Eval[Seq[EventDTO]] = Eval.later(obj)

      override def create[P <: Payload](
        `type`: EventType,
        competitionId: Option[String],
        competitorId: Option[String],
        fightId: Option[String],
        categoryId: Option[String],
        payload: Option[P]
      ): Eval[EventDTO] = {
        val evt = new EventDTO()
        evt.setPayload(payload.orNull)
        evt.setType(`type`)
        evt.setCompetitionId(competitionId.orNull)
        Eval.later(evt)
      }

      override def error(error: => Errors.Error): Eval[Either[Errors.Error, EventDTO]] = Eval.now(Left(error))
    }
  }

  import Dependencies._

//  before {
//  }

  private val numberOfCompetitors = 3

  val stage: StageDescriptorDTO = new StageDescriptorDTO().setId(stageId)

  val initialState: CompetitionStateImpl = CompetitionStateImpl(
    id = competitionId,
    competitors = Some(
      competitors.groupMapReduce(_.getId)(identity)((a, _) => a)
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
      newFights <- Eval.later(fights.groupMapReduce(_.getId)(identity)((a, _) => a))
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
