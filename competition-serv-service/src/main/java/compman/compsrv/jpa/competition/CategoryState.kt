package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CategoryStatus
import javax.persistence.*

@Entity(name = "category_state")
class CategoryState(id: String,
                    @ManyToOne(fetch = FetchType.LAZY)
                    @JoinColumn(name = "competition_id", nullable = false)
                    var competition: CompetitionProperties,
                    @OneToOne(optional = false, fetch = FetchType.LAZY)
                    @PrimaryKeyJoinColumn
                    var category: CategoryDescriptor,
                    var status: CategoryStatus,
                    @OneToOne(fetch = FetchType.LAZY)
                    @PrimaryKeyJoinColumn
                    var brackets: BracketDescriptor?,
                    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "categoryId")
                    var competitors: Set<Competitor>) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: CategoryStateDTO, props: CompetitionProperties) = CategoryState(
                id = dto.id,
                competition = props,
                category = CategoryDescriptor.fromDTO(dto.category),
                status = dto.status,
                brackets = BracketDescriptor.fromDTO(dto.brackets),
                competitors = dto.competitors.map { Competitor.fromDTO(it) }.toSet()
        )
    }

    fun toDTO(): CategoryStateDTO {
        return CategoryStateDTO(id, competition.id, category.toDTO(), status, brackets?.toDTO(), competitors.map { it.toDTO() }.toTypedArray())
    }
}

