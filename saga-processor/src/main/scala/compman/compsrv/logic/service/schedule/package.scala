package compman.compsrv.logic.service

import java.time.Instant

package object schedule {
  case class InternalFightStartTime(fightId: String,
                                    categoryId: String,
                                    matId: String,
                                    fightNumber: Int,
                                    startTime: Instant,
                                    scheduleEntryId: String,
                                    periodId: String) {
    override def equals(obj: Any): Boolean = {
      obj match {
        case time: InternalFightStartTime =>
          fightId.equals(time.fightId)
        case _ =>
          false
      }
    }

    override def hashCode(): Int = fightId.hashCode
  }

  case class InternalMatScheduleContainer()
}
