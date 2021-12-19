package compman.compsrv.logic.command

import cats.Eval
import compman.compsrv.Utils
import compman.compsrv.logic.CompetitionState
import compman.compsrv.model.Payload
import compman.compsrv.model.command.Commands.ChangeFightOrderCommand
import compman.compsrv.model.commands.payload.ChangeFightOrderPayload
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleDTO}
import compman.compsrv.model.events.EventType
import compman.compsrv.service.TestEntities
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

class ChangeFightOrderProcSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {

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

  val payload: Option[ChangeFightOrderPayload] =
    Some(new ChangeFightOrderPayload().setFightId("fight1").setNewMatId("mat2")
      .setPeriodId("period1").setNewOrderOnMat(3)
    )

  private val command = ChangeFightOrderCommand(
    payload = payload,
    competitionId = Some(competitionId),
    categoryId = Some(categoryId)
  )

  test("Should generate Fight Order changed event.") {
    val fightOrderChangedEvent = (for {
      newFights <- Eval.later(Utils.groupById(fights)(_.getId))
      result <- ChangeFightOrderProc[Eval, Payload](initialState.copy(fights = Some(newFights), schedule = Some(new ScheduleDTO()
        .setId(initialState.id)
        .setPeriods(Array(new PeriodDTO()
          .setId(periodId)))
        .setMats(Array(mat1, mat2)))))
        .apply(command)
    } yield result).value
    assert(fightOrderChangedEvent.isRight)
    val events = fightOrderChangedEvent.getOrElse(List.empty)
    assert(events.nonEmpty)
    assert(events.head.getType == EventType.FIGHT_ORDER_CHANGED)
    assert(events.head.getPayload != null)
    assert(events.head.getPayload.isInstanceOf[ChangeFightOrderPayload])
    val eventPayload = events.head.getPayload.asInstanceOf[ChangeFightOrderPayload]
    assert(eventPayload != null)
  }
}
