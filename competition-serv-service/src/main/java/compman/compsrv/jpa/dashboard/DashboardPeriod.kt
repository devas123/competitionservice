package compman.compsrv.jpa.dashboard

import compman.compsrv.jpa.AbstractJpaPersistable
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.time.Instant
import javax.persistence.Entity
import javax.persistence.OneToMany
import javax.persistence.OrderColumn

@Entity
class DashboardPeriod(id: String,
                      var name: String,
                      @OneToMany(mappedBy = "dashboardPeriod")
                      @Cascade(CascadeType.ALL)
                      @OrderColumn
                      var mats: MutableList<MatDescription>?,
                      var startTime: Instant,
                      var isActive: Boolean) : AbstractJpaPersistable<String>(id) {
    override fun toString(): String {
        return "DashboardPeriod(id='$id', name='$name', matIds='$mats', startTime=$startTime, isActive=$isActive)"
    }
}