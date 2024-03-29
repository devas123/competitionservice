package compman.compsrv.query.model

import compservice.model.protobuf.eventpayload.FightOrderUpdate
import compservice.model.protobuf.model._

import java.util.Date

case class FightOrderUpdateExtended(competitionId: String, fightOrderUpdate: FightOrderUpdate, newMat: Mat)

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
  priority: Option[Int]
)

case class Fight(
  id: String,
  fightName: Option[String],
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

case class FightReference(referenceType: FightReferenceType, fightId: String)

case class BracketsInfo(
  round: Option[Int],
  numberInRound: Option[Int],
  groupId: Option[String],
  connections: List[FightReference],
  roundType: StageRoundType
)
case class CompScore(
  placeholderId: Option[String],
  competitorId: Option[String],
  competitorFirstName: Option[String],
  competitorLastName: Option[String],
  competitorAcademyName: Option[String],
  score: Score,
  parentReferenceType: Option[FightReferenceType],
  parentFightId: Option[String]
)

case class Score(points: Int, advantages: Int, penalties: Int, pointGroups: List[PointGroup])
case class PointGroup(id: String, name: Option[String], priority: Option[Int], value: Option[Int])

case class FightResult(winnerId: Option[String], resultTypeId: Option[String], reason: Option[String])
