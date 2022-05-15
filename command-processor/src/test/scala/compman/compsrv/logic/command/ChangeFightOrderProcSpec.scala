package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.command.Commands.ChangeFightOrderCommand
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.ChangeFightOrderPayload
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model.{Period, Schedule, StageDescriptor}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

class ChangeFightOrderProcSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {

  import Dependencies._

  val stage: StageDescriptor = StageDescriptor().withId(stageId)

  val initialState: CompetitionState = CompetitionState(
    id = competitionId,
    competitors = Some(Utils.groupById(competitors)(_.id)),
    competitionProperties = None,
    stages = Some(Map(stageId -> stage)),
    fights = None,
    categories = None,
    registrationInfo = None,
    schedule = None,
    revision = 0
  )

  private val payload = Some(
    ChangeFightOrderPayload().withFightId("fight1").withNewMatId("mat2").withPeriodId("period1").withNewOrderOnMat(3)
  )

  private val command =
    ChangeFightOrderCommand(payload = payload, competitionId = Some(competitionId), categoryId = Some(categoryId))

  test("Should generate Fight Order changed event.") {
    val fightOrderChangedEvent = (for {
      newFights <- Eval.later(Utils.groupById(fights)(_.id))
      result <- ChangeFightOrderProc[Eval](initialState.copy(
        fights = Some(newFights),
        schedule =
          Some(Schedule().withId(initialState.id).withPeriods(Seq(Period().withId(periodId))).withMats(Seq(mat1, mat2)))
      )).apply(command)
    } yield result).value
    assert(fightOrderChangedEvent.isRight)
    val events = fightOrderChangedEvent.getOrElse(List.empty)
    assert(events.nonEmpty)
    assert(events.head.`type` == EventType.FIGHT_ORDER_CHANGED)
    assert(events.head.messageInfo.map(_.payload).isDefined)
    assert(events.head.messageInfo.flatMap(_.payload.changeFightOrderPayload).isDefined)
  }
}
