package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.schedule.DashboardPeriodDTO
import java.time.Instant
import javax.persistence.Entity

@Entity
class DashboardPeriod(id: String,
                      var name: String,
                      var matIds: Array<String>,
                      var startTime: Instant,
                      var isActive: Boolean) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: DashboardPeriodDTO) = DashboardPeriod(dto.id, dto.name, dto.matIds, dto.startTime, dto.isActive)
    }

    override fun toString(): String {
        return "DashboardPeriod(id='$id', name='$name', matIds='$matIds', startTime=$startTime, isActive=$isActive)"
    }

    fun toDTO(): DashboardPeriodDTO = DashboardPeriodDTO()
            .setId(id)
            .setName(name)
            .setMatIds(matIds)
            .setStartTime(startTime)
            .setIsActive(isActive)
}