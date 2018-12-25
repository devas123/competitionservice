package compman.compsrv.jpa.competition

import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CategoryStatus
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
                         val status: CategoryStatus,
                         @OneToOne
                         @MapsId
                         val brackets: BracketDescriptor?,
                         @OneToMany(cascade = [CascadeType.ALL], mappedBy = "categoryId")
                         val competitors: Set<Competitor>) {

    companion object {
        fun fromDTO(dto: CategoryStateDTO, props: CompetitionProperties) = CategoryState(
                id = dto.id,
                competition = props,
                category = CategoryDescriptor.fromDTO(dto.category),
                status = dto.status,
                brackets = BracketDescriptor.fromDTO(dto.brackets),
                competitors = dto.competitors.map { Competitor.fromDTO(it) }.toSet()
        )}

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
    fun toDTO(): CategoryStateDTO {
        return CategoryStateDTO(id, competition.id, category.toDTO(), status, brackets?.toDTO(), competitors.map { it.toDTO() }.toTypedArray())
    }
}

