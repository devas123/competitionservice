package compman.compsrv.service.schedule.internal

import java.time.Instant

data class InternalFightStartTime(val fightId: String,
                                  val categoryId: String,
                                  val matId: String,
                                  val fightNumber: Int,
                                  val startTime: Instant,
                                  val scheduleEntryId: String,
                                  val periodId: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InternalFightStartTime

        if (fightId != other.fightId) return false

        return true
    }

    override fun hashCode(): Int {
        return fightId.hashCode()
    }
}