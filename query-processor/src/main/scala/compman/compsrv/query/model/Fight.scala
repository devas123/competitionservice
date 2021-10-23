package compman.compsrv.query.model

import compman.compsrv.model.dto.brackets.{FightReferenceType, StageRoundType}
import compman.compsrv.model.dto.competition.FightStatus
import io.getquill.Udt

import java.util.Date

case class FightStartTimeUpdate(
  id: String,
  competitionId: String,
  categoryId: String,
  matId: Option[String],
  matName: Option[String],
  matOrder: Option[Int],
  numberOnMat: Option[Int],
  startTime: Option[Date],
  invalid: Option[Boolean],
  scheduleEntryId: Option[String],
  periodId: Option[String],
  priority: Option[Int],
)

case class Fight(
  id: String,
  competitionId: String,
  stageId: String,
  categoryId: String,
  matId: Option[String],
  matName: Option[String],
  matOrder: Option[Int],
  durationSeconds: Int,
  status: Option[FightStatus],
  numberOnMat: Option[Int],
  periodId: Option[String],
  startTime: Option[Date],
  invalid: Option[Boolean],
  scheduleEntryId: Option[String],
  priority: Option[Int],
  bracketsInfo: Option[BracketsInfo],
  fightResult: Option[FightResult],
  scores: List[CompScore]
)

case class BracketsInfo(
  round: Option[Int],
  numberInRound: Option[Int],
  groupId: Option[String],
  winFight: Option[String],
  loseFight: Option[String],
  roundType: StageRoundType
) extends Udt
case class CompScore(
                      placeholderId: Option[String],
                      competitorId: Option[String],
                      competitorFirstName: Option[String],
                      competitorLastName: Option[String],
                      competitorAcademyName: Option[String],
                      score: Score,
                      parentReferenceType: Option[FightReferenceType],
                      parentFightId: Option[String]
) extends Udt

case class Score(points: Int, advantages: Int, penalties: Int, pointGroups: List[PointGroup])      extends Udt
case class PointGroup(id: String, name: Option[String], priority: Option[Int], value: Option[Int]) extends Udt

case class FightResult(winnerId: Option[String], resultTypeId: Option[String], reason: Option[String]) extends Udt
