package compman.compsrv.jpa.schedule

import compman.compsrv.model.dto.schedule.DashboardPeriodDTO
import java.time.ZonedDateTime
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class DashboardPeriod(@Id val id: String,
                           val name: String,
                           val matIds: Array<String>,
                           val startTime: ZonedDateTime,
                           val isActive: Boolean) {

    companion object {
        fun fromDTO(dto: DashboardPeriodDTO) = DashboardPeriod(dto.id, dto.name, dto.matIds, dto.startTime, dto.isActive)
    }

    override fun toString(): String {
        return "DashboardPeriod(id='$id', name='$name', matIds='$matIds', startTime=$startTime, isActive=$isActive)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DashboardPeriod

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return 31
    }

    fun setActive(active: Boolean) = copy(isActive = active)
    fun addMat(s: String) = copy(matIds = matIds + s)
}