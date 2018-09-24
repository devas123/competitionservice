package compman.compsrv.model.schedule

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.util.*
import kotlin.collections.ArrayList

@JsonIgnoreProperties(ignoreUnknown = true)
data class FightScheduleInfo @JsonCreator
@PersistenceConstructor constructor(@JsonProperty("fightId") val fightId: String,
                                    @JsonProperty("orderOnTheMat") val orderOnTheMat: Int,
                                    @JsonProperty("startTime") val startTime: Date)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatScheduleContainer @JsonCreator
@PersistenceConstructor constructor(@JsonProperty("currentTime") val currentTime: Date,
                                    @JsonProperty("matId") val matId: String,
                                    @JsonProperty("fights") val fights: List<FightScheduleInfo>) {
    constructor(currentTime: Date, matId: String) : this(currentTime, matId, ArrayList())
}