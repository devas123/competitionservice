package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import java.math.BigDecimal
import javax.persistence.*


@Entity
@Table(name = "category_descriptor")
data class CategoryDescriptor(
        val sportsType: String,
        @ManyToOne
        @JoinColumn(name = "age_id")
        val ageDivision: AgeDivision,
        val gender: String,
        @ManyToOne
        @JoinColumn(name = "weight_id")
        val weight: Weight,
        val beltType: String,
        @Id
        val id: String,
        val fightDuration: BigDecimal) {
    fun setBeltType(beltType: String) = copy(beltType = beltType)
    fun setFightDuration(fightDuration: BigDecimal) = copy(fightDuration = fightDuration)
    fun setWeight(weight: Weight): CategoryDescriptor = copy(weight = weight)
    fun setAgeDivision(ageDivision: AgeDivision) = copy(ageDivision = ageDivision)
    fun toDTO(): CategoryDescriptorDTO? {
        return CategoryDescriptorDTO(sportsType, ageDivision.toDTO(), gender, weight.toDTO(), beltType, id, fightDuration)
    }

    companion object {
        fun fromDTO(dto: CategoryDescriptorDTO): CategoryDescriptor {
            return CategoryDescriptor(
                    sportsType =  dto.sportsType,
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