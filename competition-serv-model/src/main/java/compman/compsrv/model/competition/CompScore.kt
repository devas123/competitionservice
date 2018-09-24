package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor


@JsonIgnoreProperties(ignoreUnknown = true)
data class CompScore @JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("competitor") val competitor: Competitor, @JsonProperty("score") val score: Score)