package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor

@JsonIgnoreProperties(ignoreUnknown = true)
data class Score
@JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("points") val points: Int,
            @JsonProperty("advantages") val advantages: Int,
            @JsonProperty("penalties") val penalties: Int,
            @JsonProperty("competitorId") val competitorId: String) {
    fun isEmpty() = points == 0 && advantages == 0 && penalties == 0

    constructor(competitorId: String) : this(points = 0, advantages = 0, penalties = 0, competitorId = competitorId)
}