package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.logic.Operations.{EventOperations, IdOperations}
import compman.compsrv.logic.competitor.CompetitorService
import compman.compsrv.model.{CompetitionStateImpl, Errors, Payload}
import compman.compsrv.model.command.Commands.FightEditorApplyChangesCommand
import compman.compsrv.model.commands.payload.{FightEditorApplyChangesPayload, FightsCompetitorUpdated}
import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CompetitorDTO, RegistrationGroupDTO, RegistrationPeriodDTO}
import compman.compsrv.model.events.{EventDTO, EventType}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class FightEditorApplyChangesProcSpec extends AnyFunSuite with BeforeAndAfter {

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

  val competitionId = "competitionId"
  val categoryId    = "categoryId"
  val stageId       = "stageId"
  val initialState: CompetitionStateImpl = CompetitionStateImpl(
    id = competitionId,
    competitors = Some(
      CompetitorService
        .generateRandomCompetitorsForCategory(size = 5, categoryId = categoryId, competitionId = competitionId)
        .groupMapReduce(_.getId)(identity)((a, _) => a)
    ),
    competitionProperties = None,
    stages = None,
    fights = None,
    categories = None,
    registrationInfo = None,
    schedule = None,
    revision = 0
  )

  val payload: Option[FightEditorApplyChangesPayload] = Some(new FightEditorApplyChangesPayload().setStageId(stageId).setBracketsChanges(Array(
    new FightsCompetitorUpdated().setFightId("id")
  )))

  val command: FightEditorApplyChangesCommand = FightEditorApplyChangesCommand(
    payload = payload,
    competitionId = Some(competitionId),
    categoryId = Some(categoryId)
  )

  test("Mock test") { FightEditorApplyChangesProc[Eval, Payload](initialState).apply(command) }

  object Deps {
    val competitionId   = "test-competition-id"
    val groupId: String = UUID.randomUUID().toString
    val clientId        = "client"
  }
}
