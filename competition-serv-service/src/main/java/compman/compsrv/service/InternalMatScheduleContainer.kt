package compman.compsrv.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.FightDescription
import org.springframework.data.annotation.PersistenceConstructor
import java.util.*
import kotlin.collections.ArrayList

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class InternalFightStartTimePair @JsonCreator
@PersistenceConstructor constructor(@JsonProperty("fight") val fight: FightDescription,
                                    @JsonProperty("fightNumber") val fightNumber: Int,
                                    @JsonProperty("startTime") val startTime: Date)

@JsonIgnoreProperties(ignoreUnknown = true)
internal class InternalMatScheduleContainer @JsonCreator
@PersistenceConstructor constructor(@JsonProperty("currentTime") val currentTime: Date,
                                    @JsonProperty("currentFightNumber") var currentFightNumber: Int,
                                    @JsonProperty("matId") val matId: String,
                                    @JsonProperty("fights") val fights: ArrayList<InternalFightStartTimePair>,
                                    @JsonIgnore @JsonProperty("pending") val pending: ArrayList<FightDescription>) {
    constructor(currentTime: Date, matId: String) : this(currentTime, 0, matId, ArrayList(), ArrayList())
}
