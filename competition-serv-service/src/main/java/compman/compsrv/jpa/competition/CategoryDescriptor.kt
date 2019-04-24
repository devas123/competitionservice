package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import java.math.BigDecimal
import javax.persistence.*


@Entity(name = "category_descriptor")
class CategoryDescriptor(
        @Column(columnDefinition = "varchar(255)")
        var competitionId: String,
        var sportsType: String,
        @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
        @JoinColumn(name = "age_id")
        var ageDivision: AgeDivision,
        var gender: String,
        @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
        @JoinColumn(name = "weight_id")
        var weight: Weight,
        var beltType: String,
        id: String,
        var fightDuration: BigDecimal) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): CategoryDescriptorDTO? {
        return CategoryDescriptorDTO(sportsType, ageDivision.toDTO(), gender, weight.toDTO(), beltType, id, fightDuration)
    }

    companion object {
        fun fromDTO(dto: CategoryDescriptorDTO, competitionId: String): CategoryDescriptor {
            return CategoryDescriptor(
                    competitionId = competitionId,
                    sportsType = dto.sportsType ?: "BJJ", //TODO: take default value from competition
                    ageDivision = AgeDivision(dto.ageDivision.id, dto.ageDivision.minimalAge, dto.ageDivision.maximalAge),
                    gender = dto.gender,
                    weight = Weight(dto.weight.id, dto.weight.maxValue, dto.weight.minValue),
                    beltType = dto.beltType,
                    id = dto.id,
                    fightDuration = dto.fightDuration
            )
        }
    }
}