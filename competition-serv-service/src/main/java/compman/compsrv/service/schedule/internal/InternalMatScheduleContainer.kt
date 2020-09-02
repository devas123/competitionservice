package compman.compsrv.service.schedule.internal

import java.time.Instant

class InternalMatScheduleContainer(
        var currentTime: Instant,
        var name: String,
        var matOrder: Int?,
        var totalFights: Int,
        var id: String,
        var periodId: String,
        var fights: MutableList<InternalFightStartTime>,
        var timeZone: String) {

    fun addFight(f: InternalFightStartTime): InternalMatScheduleContainer {
        fights.add(f)
        return this
    }

    fun copy(currentTime: Instant = this.currentTime, name: String = this.name, matOrder: Int? = this.matOrder,
             totalFights: Int = this.totalFights, id: String = this.id, periodId: String = this.periodId,
             fights: MutableList<InternalFightStartTime> = this.fights, timeZone: String = this.timeZone): InternalMatScheduleContainer {
        this.currentTime = currentTime
        this.name = name
        this.matOrder = matOrder
        this.totalFights = totalFights
        this.id = id
        this.periodId = periodId
        this.fights = fights
        this.timeZone = timeZone
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InternalMatScheduleContainer

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}