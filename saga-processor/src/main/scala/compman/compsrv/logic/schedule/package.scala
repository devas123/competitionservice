package compman.compsrv.logic

import java.time.Instant
import scala.collection.mutable.ArrayBuffer

package object schedule {
  case class InternalFightStartTime(
    fightId: String,
    categoryId: String,
    matId: String,
    fightNumber: Int,
    startTime: Instant,
    scheduleEntryId: String,
    periodId: String
  ) {
    override def equals(obj: Any): Boolean = {
      obj match {
        case time: InternalFightStartTime => fightId.equals(time.fightId)
        case _                            => false
      }
    }

    override def hashCode(): Int = fightId.hashCode
  }

  case class InternalMatScheduleContainer(
                                           var currentTime: Instant,
                                           name: String,
                                           matOrder: Int,
                                           var totalFights: Int,
                                           id: String,
                                           periodId: String,
                                           fights: ArrayBuffer[InternalFightStartTime],
                                           timeZone: String
  ) {
    def addFight(f: InternalFightStartTime): InternalMatScheduleContainer = this.copy(fights = this.fights :+ f)
  }
}
