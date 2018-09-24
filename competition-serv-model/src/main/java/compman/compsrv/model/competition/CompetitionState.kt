package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor

data class CompetitionState @PersistenceConstructor @JsonCreator
constructor(@JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("categories") val categories: Array<CategoryState>,
            @JsonProperty("properties") val properties: CompetitionProperties?) {

    override fun equals(other: Any?): Boolean {
        if (other is CompetitionState) {
            val cats = categories.map { it.category.categoryId }.toSet()
            val otherCats = other.categories.map { it.category.categoryId }.toSet()
            return competitionId == other.competitionId && cats == otherCats
        }
        return false
    }

    override fun hashCode(): Int {
        return competitionId.hashCode()
    }
}