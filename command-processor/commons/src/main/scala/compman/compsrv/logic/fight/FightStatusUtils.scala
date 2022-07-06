package compman.compsrv.logic.fight

import compservice.model.protobuf.model.{FightDescription, FightStatus}

object FightStatusUtils {
  val unMovableStatus: Set[FightStatus] = Set(FightStatus.IN_PROGRESS, FightStatus.FINISHED)
  val completedStatus: Set[FightStatus] = Set(FightStatus.FINISHED, FightStatus.WALKOVER, FightStatus.UNCOMPLETABLE)
  val activeFightStatus: Set[FightStatus] = Set(FightStatus.PENDING, FightStatus.GET_READY, FightStatus.IN_PROGRESS, FightStatus.PAUSED)
  def isNotUncompletable(f: FightDescription): Boolean = f.status != FightStatus.UNCOMPLETABLE
  def isUncompletable(f: FightDescription): Boolean = !isNotUncompletable(f)
  def isNotMovable(f: FightDescription): Boolean = unMovableStatus.contains(f.status)
  def isCompleted(f: FightDescription): Boolean = completedStatus.contains(f.status)
}
