package compman.compsrv.model.dto

import compman.compsrv.model.competition.AgeDivision
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.Weight
import compman.compsrv.model.schedule.Schedule
import java.math.BigDecimal

data class CategoryDTO
constructor(val ageDivision: AgeDivision?,
            val gender: String?,
            val competitionId: String,
            val categoryId: String,
            val weight: Weight?,
            val beltType: String?,
            val fightDuration: BigDecimal,
            val competitorsQuantity: Int,
            val fightsNumber: Int) {
    constructor(categoryState: CategoryState) : this(
            ageDivision = categoryState.category.ageDivision,
            categoryId = categoryState.category.id,
            fightDuration = categoryState.category.fightDuration,
            beltType = categoryState.category.beltType,
            competitionId = categoryState.competition.id,
            competitorsQuantity = categoryState.competitors.size,
            fightsNumber = categoryState.brackets?.fights?.filter { !Schedule.obsoleteFight(it, categoryState.competitors.size == 3) }?.size
                    ?: 0,
            gender = categoryState.category.gender,
            weight = categoryState.category.weight
    )
}