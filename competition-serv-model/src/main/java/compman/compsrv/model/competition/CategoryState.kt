package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.brackets.BracketDescriptor

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategoryState @JsonCreator constructor(@JsonProperty("correlationId") val correlationId: String,
                                                  @JsonProperty("category") val category: Category,
                                                  @JsonProperty("brackets") val brackets: BracketDescriptor?,
                                                  @JsonProperty("competitors") val competitors: Set<Competitor>) {


    fun addCompetitor(competitor: Competitor) = copy(competitors = competitors + competitor.copy(category = this.category))
    fun removeCompetitor(email: String) = copy(competitors = competitors.asSequence().filter { it.email != email }.toSet())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CategoryState

        if (category != other.category) return false

        return true
    }

    override fun hashCode(): Int {
        return category.hashCode()
    }


    fun createSnapshot() = copy(category = category.copy(categoryId = category.categoryId + "_SNAPSHOT"))
    override fun toString(): String {
        return "CategoryState(correlationId=$correlationId, category=$category, brackets=$brackets, competitors=$competitors)"
    }

    fun withBrackets(brackets: BracketDescriptor?) = copy(brackets = brackets)


}

