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
                    var competition: CompetitionState?,
                    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
                    @PrimaryKeyJoinColumn
                    var category: CategoryDescriptor?,
                    var status: CategoryStatus?,
                    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
                    @PrimaryKeyJoinColumn
                    var brackets: BracketDescriptor?,
                    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "categoryId", fetch = FetchType.LAZY)
                    var competitors: Set<Competitor>?) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: CategoryStateDTO, competition: CompetitionState, competitors: Set<Competitor>) = CategoryState(
                id = dto.id,
                competition = competition,
                category = CategoryDescriptor.fromDTO(dto.category, competition.id!!),
                status = dto.status,
                brackets = dto.brackets?.let {
                    BracketDescriptor.fromDTO(dto.brackets)
                },
                competitors = competitors
        )

        fun fromDTO(dto: CategoryStateDTO, competition: CompetitionState) = CategoryState(
                id = dto.id,
                competition = competition,
                category = CategoryDescriptor.fromDTO(dto.category, competition.id!!),
                status = dto.status,
                brackets = dto.brackets?.let {
                    BracketDescriptor.fromDTO(dto.brackets)
                },
                competitors = dto.competitors?.map { Competitor.fromDTO(it) }?.toSet()
        )
    }

    fun toDTO(includeCompetitors: Boolean = false): CategoryStateDTO {
        return CategoryStateDTO(id, competition?.id, category?.toDTO(), status, brackets?.toDTO(), competitors?.size, if (includeCompetitors) competitors?.map { it.toDTO() }?.toTypedArray() else emptyArray())
    }

}

