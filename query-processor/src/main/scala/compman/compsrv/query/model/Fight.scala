package compman.compsrv.query.model

import compman.compsrv.model.dto.brackets.{FightReferenceType, StageRoundType}
import io.getquill.Udt

import java.time.Instant

case class Fight(
  id: String,
  competitionId: String,
  stageId: String,
  categoryId: String,
  matId: Option[String],
  scheduleInfo: Option[ScheduleInfo],
  bracketsInfo: Option[BracketsInfo],
  fightResult: Option[FightResult],
  scores: List[CompScore]
)

case class ScheduleInfo(
                         mat: Mat,
                         numberOnMat: Option[Int],
                         periodId: Option[String],
                         startTime: Option[Instant],
                         invalid: Option[Boolean],
                         scheduleEntryId: Option[String]
)                                                                                                           extends Udt
case class BracketsInfo(numberInRound: Option[Int], winFight: Option[String], loseFight: Option[String], roundType: StageRoundType) extends Udt
case class CompScore(
  placeholderId: Option[String],
  competitorId: Option[String],
  score: Score,
  parentReferenceType: Option[FightReferenceType],
  parentFightId: Option[String]
) extends Udt

case class Score(points: Int, advantages: Int, penalties: Int, pointGroups: List[PointGroup]) extends Udt
case class PointGroup(id: String, name: Option[String], priority: Option[Int], value: Option[Int])                   extends Udt

case class FightResult(winnerId: Option[String], resultTypeId: Option[String], reason: Option[String]) extends Udt
