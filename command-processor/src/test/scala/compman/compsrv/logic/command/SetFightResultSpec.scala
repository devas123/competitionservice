package compman.compsrv.logic.command

import cats.Eval
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.Utils
import compman.compsrv.logic.Operations
import compman.compsrv.model.command.Commands.{CreateFakeCompetitors, GenerateBracketsCommand, SetFightResultCommand}
import compman.compsrv.model.Errors
import compman.compsrv.service.TestEntities
import compservice.model.protobuf.commandpayload.{GenerateBracketsPayload, SetFightResultPayload}
import compservice.model.protobuf.event.EventType
import compservice.model.protobuf.model._
import compservice.model.protobuf.model.FightStatus.UNCOMPLETABLE
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class SetFightResultSpec extends AnyFunSuite with BeforeAndAfter with TestEntities {
  import Dependencies._

  override val initialState: CommandProcessorCompetitionState = CommandProcessorCompetitionState(
    id = competitionId,
    competitors = Utils.groupById(competitors)(_.id),
    competitionProperties = None,
    stages = Map(stageId -> stage),
    fights = Utils.groupById(fights)(_.id),
    categories = Map.empty,
    registrationInfo = None,
    schedule = None
  )

  val fight: FightDescription = fights.head

  val payload: Option[SetFightResultPayload] = Some(
    SetFightResultPayload().withFightId(fight.id).withStatus(FightStatus.FINISHED)
      .withFightResult(FightResult().withReason("dota2").withResultTypeId("_default_win_points").withWinnerId(
        fight.scores.head.getCompetitorId
      )).withScores(fight.scores)
  )

  val command: SetFightResultCommand =
    SetFightResultCommand(payload = payload, competitionId = Some(competitionId), categoryId = Some(categoryId))

  test("Should set fight result and propagate competitors") {
    val updatedFights = (for { result <- SetFightResultProc[Eval](initialState).apply(command) } yield result).value
    assert(updatedFights.isRight)
    val events = updatedFights.getOrElse(List.empty)
    assert(events.nonEmpty)
  }

  test("Should set fight result and propagate competitors for Double elimination") {
    val state = initialState.clearCompetitors.clearFights
      .addCategories((categoryId, CategoryDescriptor().withId(categoryId).withName("Test")))
    val createFakeCompetitorsCommand =
      CreateFakeCompetitors(competitionId = Some(competitionId), categoryId = Some(categoryId))
    val updatedFights = (for {
      fff                  <- EitherT(CreateFakeCompetitorsProc[Eval]().apply(createFakeCompetitorsCommand))
      stateWithCompetitors <- EitherT.liftF(fff.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      generateBracketsPayload = GenerateBracketsPayload().addStageDescriptors(
        StageDescriptor().withId(UUID.randomUUID().toString).withName("test").withStageType(StageType.FINAL)
          .withBracketType(BracketType.DOUBLE_ELIMINATION).withCategoryId(categoryId).withCompetitionId(competitionId)
          .withFightDuration(300).withStageOrder(0).withWaitForPrevious(false).withHasThirdPlaceFight(false)
          .withStageResultDescriptor(
            StageResultDescriptor().withName("test").withOutputSize(0).withForceManualAssignment(false)
          )
      )
      generateBracketsCommand =
        GenerateBracketsCommand(Some(generateBracketsPayload), Some(competitionId), Some(categoryId))
      bracketsGenerates <- EitherT(GenerateBracketsProc[Eval](stateWithCompetitors).apply(generateBracketsCommand))
      updatedState <- EitherT.liftF(bracketsGenerates.toList.foldM(state)((s, e) => Operations.applyEvent[Eval](s, e)))
      loserFightUncompletable <- EitherT.fromOption[Eval](
        updatedState.fights.values.find(f => f.roundType == StageRoundType.LOSER_BRACKETS && f.status == UNCOMPLETABLE),
        Errors.InternalError("Cannot find appropriate loser fight")
      )
      parentFightId <- EitherT.fromOption[Eval](
        loserFightUncompletable.scores.find(_.parentFightId.isDefined).flatMap(_.parentFightId),
        Errors.InternalError("Cannot find parent fight")
      )
      parentFight = updatedState.fights(parentFightId)
      payload = SetFightResultPayload().withFightId(parentFight.id).withStatus(FightStatus.FINISHED)
        .withFightResult(FightResult().withReason("dota2").withResultTypeId("_default_win_points").withWinnerId(
          parentFight.scores.head.getCompetitorId
        )).withScores(parentFight.scores)
      command = SetFightResultCommand(
        payload = Some(payload),
        competitionId = Some(competitionId),
        categoryId = Some(categoryId)
      )
      result <- EitherT(SetFightResultProc[Eval](updatedState).apply(command))
    } yield result).value.value
    assert(updatedFights.isRight)
    updatedFights match {
      case Left(_) => fail("Impossible")
      case Right(value) =>
        assert(value.size > 2)
        val last = value.last
        assert(last.messageInfo.isDefined)
        assert(last.messageInfo.get.payload.isFightCompetitorsAssignedPayload)
        assert(last.messageInfo.get.payload.fightCompetitorsAssignedPayload.get.assignments.size > 1)
    }
  }

  test("Should set stage result.") {
    val finishedFights = fights.map(f =>
      f.withStatus(FightStatus.FINISHED).withFightResult(
        FightResult().withReason("test").withWinnerId(f.scores.head.getCompetitorId).withResultTypeId("default")
      )
    )
    val updatedFights = (for {
      fight1Update <- SetFightResultProc[Eval](initialState.copy(
        fights = Utils.groupById(finishedFights)(_.id),
        stages = Map(stageId -> singleEliminationBracketsStage)
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
