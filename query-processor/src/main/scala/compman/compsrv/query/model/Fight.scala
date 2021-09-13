package compman.compsrv.query.model

import compman.compsrv.model.dto.brackets.FightReferenceType
import io.getquill.Udt

import java.time.Instant

case class Fight(
  id: String,
  competitionId: String,
  categoryId: String,
  scheduleInfo: Option[ScheduleInfo],
  bracketsInfo: Option[BracketsInfo],
  fightResult: String,
  scores: Set[CompScore]
)

case class ScheduleInfo(matId: String, matName: String, numberOnMat: Int, periodId: String, startTime: Instant)
    extends Udt
case class BracketsInfo(stageId: String, numberInRound: Int, numberOnMat: Int, winFight: String, loseFight: String)
    extends Udt
case class CompScore(
  placeholderId: String,
  competitorId: String,
  score: Score,
  order: Int,
  parentReferenceType: FightReferenceType,
  parentFightId: String
) extends Udt

case class Score(points: Integer, advantages: Integer, penalties: Integer, pointGroups: Set[PointGroup]) extends Udt
case class PointGroup(id: String, name: String, priority: Int, value: Int)                               extends Udt
