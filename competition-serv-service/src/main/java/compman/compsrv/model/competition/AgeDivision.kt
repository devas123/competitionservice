package compman.compsrv.model.competition

import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class AgeDivision(
        @Id val id: String,
        val minimalAge: Int,
        val maximalAge: Int) {
    constructor(name: String, minimalAge: Int) : this(name, minimalAge, Int.MAX_VALUE)
}

object BjjAgeDivisions {
    val MIGHTY_MITE_I = AgeDivision("MIGHTY MITE I", 4, 4)
    val MIGHTY_MITE_II = AgeDivision("MIGHTY MITE II", 5, 5)
    val MIGHTY_MITE_III = AgeDivision("MIGHTY MITE III", 6, 6)
    val PEE_WEE_I = AgeDivision("PEE WEE I", 7, 7)
    val PEE_WEE_II = AgeDivision("PEE WEE II", 8, 8)
    val PEE_WEE_III = AgeDivision("PEE WEE III", 9, 9)
    val JUNIOR_I = AgeDivision("JUNIOR I", 10, 10)
    val JUNIOR_II = AgeDivision("JUNIOR II", 11, 11)
    val JUNIOR_III = AgeDivision("JUNIOR III", 12, 12)
    val TEEN_I = AgeDivision("TEEN I", 13, 13)
    val TEEN_II = AgeDivision("TEEN II", 14, 14)
    val TEEN_III = AgeDivision("TEEN III", 15, 15)
    val JUVENILE_I = AgeDivision("JUVENILE I", 16, 16)
    val JUVENILE_II = AgeDivision("JUVENILE II", 17, 17)
    val ADULT = AgeDivision("ADULT", 18)
    val MASTER_1 = AgeDivision("MASTER 1", 30)
    val MASTER_2 = AgeDivision("MASTER 2", 36)
    val MASTER_3 = AgeDivision("MASTER 3", 41)
    val MASTER_4 = AgeDivision("MASTER 4", 46)
    val MASTER_5 = AgeDivision("MASTER 5", 51)
    val MASTER_6 = AgeDivision("MASTER 6", 56)

    val allDivisions = listOf(MIGHTY_MITE_I,
            MIGHTY_MITE_II, MIGHTY_MITE_III, PEE_WEE_I,
            PEE_WEE_II, PEE_WEE_III, JUNIOR_I, JUNIOR_II,
            JUNIOR_III, TEEN_I, TEEN_II, TEEN_III,
            JUVENILE_I, JUVENILE_II, ADULT, MASTER_1,
            MASTER_2, MASTER_3, MASTER_4, MASTER_5, MASTER_6)

    val adultDivisions = listOf(ADULT, MASTER_1,
            MASTER_2, MASTER_3, MASTER_4, MASTER_5, MASTER_6)

    val infantileDivisions = listOf(MIGHTY_MITE_I,
            MIGHTY_MITE_II, MIGHTY_MITE_III, PEE_WEE_I,
            PEE_WEE_II, PEE_WEE_III, JUNIOR_I, JUNIOR_II,
            JUNIOR_III, TEEN_I, TEEN_II, TEEN_III,
            JUVENILE_I, JUVENILE_II)


    fun getAvailableAgeDivisions(birthDate: LocalDate): List<AgeDivision> {
        val age = LocalDate.now().year - birthDate.year
        return allDivisions.filter { (_, minimalAge, maximalAge) -> age in minimalAge..maximalAge }
    }
}