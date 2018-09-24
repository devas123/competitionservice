package compman.compsrv.model.schedule

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.Category
import java.math.BigDecimal
import java.util.*

data class PeriodProperties
@JsonCreator
constructor(@JsonProperty("id") val id: String,
            @JsonProperty("startTime") val startTime: Date,
            @JsonProperty("numberOfMats") val numberOfMats: Int,
            @JsonProperty("timeBetweenFights") val timeBetweenFights: Int,
            @JsonProperty("riskPercent") val riskPercent: BigDecimal,
            @JsonProperty("categories") val categories: List<Category>)

data class ScheduleProperties @JsonCreator
constructor(@JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("periodPropertiesList") val periodPropertiesList: List<PeriodProperties>)