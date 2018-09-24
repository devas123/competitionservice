package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class GenerateAbsoluteMessage @JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("competitors") val competitors: Array<Competitor>,
            @JsonProperty("category") val category: Category,
            @JsonProperty("competitionId") val competitionId: String) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GenerateAbsoluteMessage

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