package compman.compsrv.model.schedule

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal

data class Period
@JsonCreator
@PersistenceConstructor
constructor(@Id @JsonProperty("id") val id: String,
            @JsonProperty("name") val name: String,
            @JsonProperty("schedule") val schedule: List<ScheduleEntry>,
            @JsonProperty("startTime") val startTime: String,
            @JsonProperty("duration") val duration: BigDecimal?,
            @JsonProperty("numberOfMats") val numberOfMats: Int,
            @JsonProperty("fightsByMats") val fightsByMats: List<MatScheduleContainer>?) {
    fun setSchedule(schedule: List<ScheduleEntry>) = copy(schedule = schedule)
}