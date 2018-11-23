package compman.compsrv.model.schedule

import java.math.BigDecimal
import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Column
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
data class ScheduleEntry(
        @Column(name = "CATEGORY_ID", columnDefinition = "varchar(255) REFERENCES category_descriptor (id)")
        val categoryId: String,
        val startTime: String,
        val numberOfFights: Int,
        val fightDuration: BigDecimal)