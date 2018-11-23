package compman.compsrv.model.competition

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
}