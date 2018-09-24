package compman.compsrv.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.Category
import compman.compsrv.model.schedule.ScheduleEntry
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor

internal data class InternalPeriod
@JsonCreator
@PersistenceConstructor
constructor(@Id @JsonProperty("id") val id: String,
            @JsonProperty("name") val name: String,
            @JsonProperty("schedule") val schedule: List<ScheduleEntry>,
            @JsonProperty("categories") val categories: List<Category>,
            @JsonProperty("startTime") val startTime: String,
            @JsonProperty("numberOfMats") val numberOfMats: Int,
            @JsonProperty("fightsByMats") val fightsByMats: ArrayList<InternalMatScheduleContainer>?)