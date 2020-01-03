package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.repository.FightCrudRepository
import org.hibernate.annotations.Cascade
import java.time.Instant
import javax.persistence.*

@Entity
class Period(id: String,
             var name: String,
             @ElementCollection
             @CollectionTable(
                     name = "SCHEDULE_ENTRIES",
                     joinColumns = [JoinColumn(name = "PERIOD_ID")]
             )
             @OrderColumn
             var schedule: List<ScheduleEntry>,
             @OneToMany(fetch = FetchType.LAZY)
             @JoinColumn(name = "PERIOD_ID", nullable = true)
             var categories: List<CategoryDescriptor>,
             var startTime: Instant,
             var numberOfMats: Int,
             @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
             @Cascade(org.hibernate.annotations.CascadeType.ALL)
             @JoinColumn(name = "PERIOD_ID", nullable = false)
             var fightsByMats: List<MatScheduleContainer>?) : AbstractJpaPersistable<String>(id) {
}