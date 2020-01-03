package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.model.dto.schedule.PeriodPropertiesDTO
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import org.hibernate.annotations.Cascade
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.*

@Entity
class PeriodProperties(id: String,
                       var name: String,
                       var startTime: Instant,
                       var numberOfMats: Int,
                       var timeBetweenFights: Int,
                       var riskPercent: BigDecimal,
                       @OneToMany(fetch = FetchType.LAZY)
                       @JoinColumn(name = "PERIOD_PROPERTIES_ID", nullable = true)
                       var categories: List<CategoryDescriptor>) : AbstractJpaPersistable<String>(id)
@Embeddable
@Access(AccessType.FIELD)
class ScheduleProperties(var id: String,
                         @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                         @Cascade(org.hibernate.annotations.CascadeType.ALL)
                         @JoinColumn(name = "SCHED_ID")
                         var periodPropertiesList: List<PeriodProperties?>?)