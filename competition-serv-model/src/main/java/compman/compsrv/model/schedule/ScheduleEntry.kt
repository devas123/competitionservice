package compman.compsrv.model.schedule

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal

data class ScheduleEntry @JsonCreator
@PersistenceConstructor
constructor(
        @JsonProperty("categoryId") val categoryId: String,
        @JsonProperty("startTime") val startTime: String,
        @JsonProperty("numberOfFights") val numberOfFights: Int,
        @JsonProperty("fightDuration") val fightDuration: BigDecimal)