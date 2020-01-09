package compman.compsrv.jpa.schedule

import compman.compsrv.model.dto.schedule.ScheduleEntryDTO
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Column
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
class ScheduleEntry(
        @Column(name = "CATEGORY_ID", columnDefinition = "varchar(255) REFERENCES category_descriptor (id)")
        var categoryId: String,
        var startTime: Instant,
        var numberOfFights: Int,
        var fightDuration: BigDecimal) {
}