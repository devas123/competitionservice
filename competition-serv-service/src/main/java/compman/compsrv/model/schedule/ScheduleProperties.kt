package compman.compsrv.model.schedule

import compman.compsrv.model.competition.CategoryDescriptor
import java.math.BigDecimal
import java.util.*
import javax.persistence.*

@Entity
data class PeriodProperties(@Id val id: String,
                            val startTime: Date,
                            val numberOfMats: Int,
                            val timeBetweenFights: Int,
                            val riskPercent: BigDecimal,
                            @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                            @JoinColumn(name="PERIOD_ID", nullable = true)
                            val categories: List<CategoryDescriptor>)

@Embeddable
@Access(AccessType.FIELD)
data class ScheduleProperties(val competitionId: String,
                              @OneToMany(orphanRemoval = true)
                              @JoinColumn(name="SCHED_ID")
                              val periodPropertiesList: List<PeriodProperties>)