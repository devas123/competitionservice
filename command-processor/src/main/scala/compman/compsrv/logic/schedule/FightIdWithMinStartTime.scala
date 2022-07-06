package compman.compsrv.logic.schedule

import java.time.Instant

case class FightIdWithMinStartTime(fightId: String, minStartTime: Option[Instant])

object FightIdWithMinStartTime {
  def compareTwoMinStartTimes(first: Option[Instant], second: Option[Instant]): Boolean = {
    if (first.isEmpty) { return true }
    if (second.isEmpty) { return false }
    first.get.isBefore(second.get)
  }
}
