package compman.compsrv.model.competition

import compman.compsrv.model.brackets.BracketDescriptor
import javax.persistence.*

@Entity
@Table(name = "category_state")
data class CategoryState(@Id
                         val id: String,
                         @ManyToOne(fetch = FetchType.LAZY)
                         @JoinColumn(name = "competition_id", nullable = false)
                         val competition: CompetitionProperties,
                         @OneToOne(optional = false)
                         @MapsId
                         val category: CategoryDescriptor,
                         val status: CategoryStateStatus,
                         @OneToOne
                         @MapsId
                         val brackets: BracketDescriptor?,
                         @OneToMany(cascade = [CascadeType.ALL], mappedBy = "categoryId")
                         val competitors: Set<Competitor>) {

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

    fun withBrackets(brackets: BracketDescriptor?) = copy(brackets = brackets)
    fun removeCompetitor(id: String) = copy(competitors = competitors.filter { it.id != id }.toSet())
    fun addCompetitor(competitor: Competitor) = copy(competitors = competitors + competitor)
}

