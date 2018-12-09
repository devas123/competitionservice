package compman.compsrv.model.es.commands.payload

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.CategoryDescriptor
import compman.compsrv.model.competition.Competitor
import org.springframework.data.annotation.PersistenceConstructor
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class GenerateAbsoluteMessagePayload @JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("competitors") val competitors: Array<Competitor>,
            @JsonProperty("category") val category: CategoryDescriptor,
            @JsonProperty("competitionId") val competitionId: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GenerateAbsoluteMessagePayload

        if (!Arrays.equals(competitors, other.competitors)) return false
        if (category != other.category) return false
        if (competitionId != other.competitionId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(competitors)
        result = 31 * result + category.hashCode()
        result = 31 * result + competitionId.hashCode()
        return result
    }

}