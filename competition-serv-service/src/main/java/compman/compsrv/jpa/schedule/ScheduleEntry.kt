package compman.compsrv.jpa.schedule

import compman.compsrv.model.dto.schedule.ScheduleEntryDTO
import java.math.BigDecimal
import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Column
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
class ScheduleEntry(
        @Column(name = "CATEGORY_ID", columnDefinition = "varchar(255) REFERENCES category_descriptor (id)")
        var categoryId: String,
        var startTime: String,
        var numberOfFights: Int,
        var fightDuration: BigDecimal) {
    fun toDTO(): ScheduleEntryDTO {
        return ScheduleEntryDTO()
                .setCategoryId(categoryId)
                .setStartTime(startTime)
                .setNumberOfFights(numberOfFights)
                .setFightDuration(fightDuration)
    }

    companion object {
            fun fromDTO(dto: ScheduleEntryDTO) =
                    ScheduleEntry(
                            categoryId = dto.categoryId,
                            startTime = dto.startTime,
                            numberOfFights =  dto.numberOfFights,
                            fightDuration = dto.fightDuration
                    )
        }
}