package compman.compsrv.query.model

import compman.compsrv.model.dto.brackets.{FightReferenceType, StageRoundType}
import io.getquill.Udt

import java.time.Instant

case class Fight(
  id: String,
  competitionId: String,
  stageId: String,
  categoryId: String,
  scheduleInfo: Option[ScheduleInfo],
  bracketsInfo: Option[BracketsInfo],
  fightResult: Option[FightResult],
  scores: Set[CompScore]
)

case class ScheduleInfo(
  mat: Option[Mat] = None,
  numberOnMat: Option[Int] = None,
  periodId: Option[String] = None,
  startTime: Option[Instant] = None,
  invalid: Option[Boolean] = None,
  scheduleEntryId: Option[String] = None
)                                                                                                           extends Udt
case class BracketsInfo(numberInRound: Int, winFight: String, loseFight: String, roundType: StageRoundType) extends Udt
case class CompScore(
  placeholderId: String,
  competitorId: String,
  score: Score,
  order: Int,
  parentReferenceType: FightReferenceType,
  parentFightId: String
) extends Udt

case class Score(points: Int, advantages: Int, penalties: Int, pointGroups: Set[PointGroup]) extends Udt
case class PointGroup(id: String, name: String, priority: Int, value: Int)                   extends Udt

case class FightResult(winnerId: String, resultTypeId: String, reason: String) extends Udt
