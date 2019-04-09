package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.WeightDTO
import compman.compsrv.util.IDGenerator
import java.math.BigDecimal
import javax.persistence.Entity

@Entity
class Weight(id: String,
             var name: String,
             var maxValue: BigDecimal?,
             var minValue: BigDecimal?) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): WeightDTO? {
        return WeightDTO(name, maxValue ?: BigDecimal.valueOf(200), minValue)
    }

    constructor(name: String,
                maxValue: BigDecimal?,
                minValue: BigDecimal?) : this(getId(name), name, maxValue, minValue)

    constructor(name: String, maxvalue: BigDecimal) : this(name, maxvalue, BigDecimal.ZERO)

    companion object {
        fun getId(name: String) = IDGenerator.hashString(name)

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