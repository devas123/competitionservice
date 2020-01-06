package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.FightCrudRepository
import org.hibernate.annotations.Cascade
import java.math.BigDecimal
import java.math.RoundingMode
import javax.persistence.*

@Entity
class Schedule(id: String,
               @Embedded
               @AttributeOverrides(
                       AttributeOverride(name = "id", column = Column(name = "properties_id"))
               )
               var scheduleProperties: ScheduleProperties?,
               @OneToMany(orphanRemoval = true)
               @Cascade(org.hibernate.annotations.CascadeType.ALL)
               @JoinColumn(name = "SCHED_ID")
               var periods: MutableList<Period>?) : AbstractJpaPersistable<String>(id)