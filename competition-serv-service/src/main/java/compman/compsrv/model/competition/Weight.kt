package compman.compsrv.model.competition

import javax.persistence.Id
import java.math.BigDecimal
import javax.persistence.Entity

@Entity
data class Weight(@Id val id: String,
                  val maxValue: BigDecimal?,
                  val minValue: BigDecimal?) {
    constructor(id: String, maxvalue: BigDecimal) : this(id, maxvalue, BigDecimal.ZERO)

    companion object {
        const val ROOSTER = "Rooster"

        const val LIGHT_FEATHER = "LightFeather"

        const val FEATHER = "Feather"

        const val LIGHT = "Light"

        const val MIDDLE = "Middle"

        const val MEDIUM_HEAVY = "Medium Heavy"

        const val HEAVY = "Heavy"

        const val SUPER_HEAVY = "Super Heavy"

        const val ULTRA_HEAVY = "Ultra Heavy"

        const val OPEN_CLASS = "Open class"

        val WEIGHT_NAMES = listOf(ROOSTER, LIGHT_FEATHER, FEATHER, LIGHT, MIDDLE,
                MEDIUM_HEAVY, HEAVY, SUPER_HEAVY, ULTRA_HEAVY, OPEN_CLASS)

        fun compareWeightNames(w1: String, w2: String) = Comparator.comparingInt { w: String -> WEIGHT_NAMES.indexOfFirst { it.toLowerCase() == w.trim().toLowerCase() } }.compare(w1, w2)
    }
}