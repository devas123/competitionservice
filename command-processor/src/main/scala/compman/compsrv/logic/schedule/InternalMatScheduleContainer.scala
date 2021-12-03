package compman.compsrv.logic.schedule

import java.time.Instant
import scala.collection.mutable.ArrayBuffer

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