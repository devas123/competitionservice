package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import java.math.BigDecimal
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne


@Entity(name = "category_descriptor")
class CategoryDescriptor(
        var sportsType: String,
        @ManyToOne
        @JoinColumn(name = "age_id")
        var ageDivision: AgeDivision,
        var gender: String,
        @ManyToOne
        @JoinColumn(name = "weight_id")
        var weight: Weight,
        var beltType: String,
        id: String,
        var fightDuration: BigDecimal) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): CategoryDescriptorDTO? {
        return CategoryDescriptorDTO(sportsType, ageDivision.toDTO(), gender, weight.toDTO(), beltType, id, fightDuration)
    }

    companion object {
        fun fromDTO(dto: CategoryDescriptorDTO): CategoryDescriptor {
            return CategoryDescriptor(
                    sportsType = dto.sportsType,
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