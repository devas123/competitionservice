package compman.compsrv.query.model

import io.getquill.Udt

import java.time.Instant

case class Fight(
                  id: String,
                  competitionId: String,
                  categoryId: String,
                  scheduleInfo: Option[ScheduleInfo],
                  bracketsInfo: Option[BracketsInfo],
                  fightResult: String,
                  scores: String)

case class ScheduleInfo(matId: String, matName: String, numberOnMat: Int, periodId: String, startTime: Instant) extends Udt
case class BracketsInfo(stageId: String, numberInRound: Int, numberOnMat: Int, winFight: String, loseFight: String) extends Udt