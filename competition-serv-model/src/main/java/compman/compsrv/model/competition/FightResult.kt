package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor

@JsonIgnoreProperties(ignoreUnknown = true)

data class FightResult @JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("winnerId") val winnerId: String?,
            @JsonProperty("draw") val draw: Boolean?,
            @JsonProperty("reason") var reason: String?)