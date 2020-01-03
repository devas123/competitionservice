package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.schedule.DashboardPeriodDTO
import java.time.Instant
import javax.persistence.ElementCollection
import javax.persistence.Entity
import javax.persistence.OrderColumn

@Entity
class DashboardPeriod(id: String,
                      var name: String,
                      @ElementCollection
                      var matIds: MutableSet<String>?,
                      var startTime: Instant,
                      var isActive: Boolean) : AbstractJpaPersistable<String>(id) {
    override fun toString(): String {
        return "DashboardPeriod(id='$id', name='$name', matIds='$matIds', startTime=$startTime, isActive=$isActive)"
    }
}