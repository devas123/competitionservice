package compman.compsrv.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.AgeDivision
import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.Weight
import compman.compsrv.model.schedule.Schedule
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal

data class CategoryDTO @PersistenceConstructor @JsonCreator
constructor(@JsonProperty("ageDivision") val ageDivision: AgeDivision?,
            @JsonProperty("gender") val gender: String?,
            @JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("categoryId") val categoryId: String?,
            @JsonProperty("weight") val weight: Weight?,
            @JsonProperty("beltType") val beltType: String?,
            @JsonProperty("fightDuration") val fightDuration: BigDecimal,
            @JsonProperty("competitorsQuantity") val competitorsQuantity: Int,
            @JsonProperty("fightsNumber") val fightsNumber: Int) {
    constructor(categoryState: CategoryState) : this(
            ageDivision = categoryState.category.ageDivision,
            categoryId = categoryState.category.categoryId,
            fightDuration = categoryState.category.fightDuration,
            beltType = categoryState.category.beltType,
            competitionId = categoryState.category.competitionId,
            competitorsQuantity = categoryState.competitors.size,
            fightsNumber = categoryState.brackets?.fights?.filter { !Schedule.obsoleteFight(it, categoryState.competitors.size == 3) }?.size ?: 0,
            gender = categoryState.category.gender,
            weight = categoryState.category.weight
    )

    fun toCategory() = Category(ageDivision, gender, competitionId, categoryId, weight, beltType, fightDuration)
}