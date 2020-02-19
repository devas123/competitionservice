package compman.compsrv.service

import compman.compsrv.model.dto.competition.*
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class CategoryGeneratorService {
    companion object {
        val bjj: CategoryRestrictionDTO = CategoryRestrictionDTO().setId(UUID.randomUUID().toString()).setType(CategoryRestrictionType.Value).setName("Sport").setValue("BJJ")
        private fun weightRestriction(maxValue: String, minValue: String = "0", alias: String = ""): CategoryRestrictionDTO = CategoryRestrictionDTO()
                .setId(IDGenerator.hashString("Weight/$minValue/$maxValue"))
                .setType(CategoryRestrictionType.Range)
                .setName("Weight")
                .setMaxValue(maxValue)
                .setMinValue(minValue)
                .setAlias(alias)
                .setUnit("kg")

        private fun ageRestriction(alias: String = "", minValue: String, maxValue: String = minValue): CategoryRestrictionDTO = CategoryRestrictionDTO()
                .setId(IDGenerator.hashString("Age/$minValue/$maxValue"))
                .setType(CategoryRestrictionType.Range)
                .setName("Age")
                .setMaxValue(maxValue)
                .setMinValue(minValue)
                .setAlias(alias)
                .setUnit("y.o.")

        private fun beltRestriction(value: String): CategoryRestrictionDTO = CategoryRestrictionDTO().setId(IDGenerator.hashString(value)).setType(CategoryRestrictionType.Value)
                .setValue(value)
                .setName("Belt")


        private fun genderRestriction(value: String): CategoryRestrictionDTO = CategoryRestrictionDTO().setId(IDGenerator.hashString(value)).setType(CategoryRestrictionType.Value)
                .setValue(value)
                .setName("Gender")

        val male = genderRestriction("MALE")
        val female = genderRestriction("FEMALE")

        val white = beltRestriction(BeltType.WHITE.name)
        val gray = beltRestriction(BeltType.GRAY.name)
        val yellow = beltRestriction(BeltType.YELLOW.name)
        val orange = beltRestriction(BeltType.ORANGE.name)
        val green = beltRestriction(BeltType.GREEN.name)
        val blue = beltRestriction(BeltType.BLUE.name)
        val purple = beltRestriction(BeltType.PURPLE.name)
        val brown = beltRestriction(BeltType.BROWN.name)
        val black = beltRestriction(BeltType.BLACK.name)

        val adult = ageRestriction(AgeDivisionDTO.ADULT, "18", "100")
        val junior1 = ageRestriction(AgeDivisionDTO.JUNIOR_I, "10")
        val junior2 = ageRestriction(AgeDivisionDTO.JUNIOR_II, "11")
        val junior3 = ageRestriction(AgeDivisionDTO.JUNIOR_III, "12")
        val juvenile1 = ageRestriction(AgeDivisionDTO.JUVENILE_I, "16")
        val juvenile2 = ageRestriction(AgeDivisionDTO.JUVENILE_II, "17")
        val master1 = ageRestriction(AgeDivisionDTO.MASTER_1, "30", "100")
        val master2 = ageRestriction(AgeDivisionDTO.MASTER_2, "35", "100")
        val master3 = ageRestriction(AgeDivisionDTO.MASTER_3, "40", "100")
        val master4 = ageRestriction(AgeDivisionDTO.MASTER_4, "45", "100")
        val master5 = ageRestriction(AgeDivisionDTO.MASTER_5, "50", "100")
        val master6 = ageRestriction(AgeDivisionDTO.MASTER_6, "55", "100")
        val mightyMite1 = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_I, "4")
        val mightyMite2 = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_II, "5")
        val mightyMite3 = ageRestriction(AgeDivisionDTO.MIGHTY_MITE_III, "6")
        val peeWee1 = ageRestriction(AgeDivisionDTO.PEE_WEE_I, "7")
        val peeWee2 = ageRestriction(AgeDivisionDTO.PEE_WEE_II, "8")
        val peeWee3 = ageRestriction(AgeDivisionDTO.PEE_WEE_III, "9")
        val teen1 = ageRestriction(AgeDivisionDTO.TEEN_I, "13")
        val teen2 = ageRestriction(AgeDivisionDTO.TEEN_II, "14")
        val teen3 = ageRestriction(AgeDivisionDTO.TEEN_III, "15")

        val admrooster = weightRestriction(WeightDTO.ROOSTER, "57.5")
        val admlightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "64")
        val admfeather = weightRestriction(WeightDTO.FEATHER, "70")
        val admlight = weightRestriction(WeightDTO.LIGHT, "76")
        val admmiddle = weightRestriction(WeightDTO.MIDDLE, "82.3")
        val admmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "88.3")
        val admheavy = weightRestriction(WeightDTO.HEAVY, "94.3")
        val admsuperHeavy = weightRestriction(WeightDTO.SUPER_HEAVY, "100.5")
        val jmrooster = weightRestriction(WeightDTO.ROOSTER, "53.5")

        val jmlightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "58.5")
        val jmfeather = weightRestriction(WeightDTO.FEATHER, "64")
        val jmlight = weightRestriction(WeightDTO.LIGHT, "69")
        val jmmiddle = weightRestriction(WeightDTO.MIDDLE, "74")
        val jmmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "79.3")
        val jmheavy = weightRestriction(WeightDTO.HEAVY, "84.3")
        val jmsuperHeavy = weightRestriction(WeightDTO.SUPER_HEAVY, "89.3")

        val multraHeavy = weightRestriction(WeightDTO.ULTRA_HEAVY, "300")
        val openClass = weightRestriction(WeightDTO.OPEN_CLASS, "300")

        val adflightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "53.5")
        val adffeather = weightRestriction(WeightDTO.FEATHER, "58.5")
        val adflight = weightRestriction(WeightDTO.LIGHT, "64")
        val adfmiddle = weightRestriction(WeightDTO.MIDDLE, "69")
        val adfmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "74")

        val jflightFeather = weightRestriction(WeightDTO.LIGHT_FEATHER, "48.3")
        val jffeather = weightRestriction(WeightDTO.FEATHER, "52.5")
        val jflight = weightRestriction(WeightDTO.LIGHT, "56.5")
        val jfmiddle = weightRestriction(WeightDTO.MIDDLE, "60.5")
        val jfmediumHeavy = weightRestriction(WeightDTO.MEDIUM_HEAVY, "65")

        val fheavy = weightRestriction(WeightDTO.HEAVY, "300")

        fun createCategory(fightDuration: Long, vararg restrictions: CategoryRestrictionDTO, registrationOpen: Boolean = true): CategoryDescriptorDTO =
                CategoryDescriptorDTO()
                        .setRestrictions(restrictions)
                        .setFightDuration(BigDecimal.valueOf(fightDuration))
                        .setRegistrationOpen(registrationOpen)
                        .apply {
                            id = IDGenerator.categoryId(this)
                        }
    }

    fun createDefaultBjjCategories(competitionId: String): List<CategoryDescriptorDTO> {

   /*     fun createMaleAdultBeltWeights(duration: Long, belt: CategoryRestrictionDTO) = listOf(
                createCategory(duration, bjj, adult, male, admrooster, belt),
                createCategory(duration, bjj, adult, male, admlightFeather, belt),
                createCategory(duration, bjj, adult, male, admfeather, belt),
                createCategory(duration, bjj, adult, male, admlight, belt),
                createCategory(duration, bjj, adult, male, admmiddle, belt),
                createCategory(duration, bjj, adult, male, admmediumHeavy, belt),
                createCategory(duration, bjj, adult, male, admheavy, belt),
                createCategory(duration, bjj, adult, male, admsuperHeavy, belt),
                createCategory(duration, bjj, adult, male, multraHeavy, belt)
        )

        fun createFemaleAdultBeltWeights(duration: Long, belt: CategoryRestrictionDTO) = listOf(
                createCategory(duration, bjj, adult, female, admlightFeather, belt),
                createCategory(duration, bjj, adult, female, admfeather, belt),
                createCategory(duration, bjj, adult, female, admlight, belt),
                createCategory(duration, bjj, adult, female, admmiddle, belt),
                createCategory(duration, bjj, adult, female, admmediumHeavy, belt),
                createCategory(duration, bjj, adult, female, admheavy, belt)
        )

        val maleAdult = createMaleAdultBeltWeights(5, white) + createMaleAdultBeltWeights(6, blue) + createMaleAdultBeltWeights(7, purple) + createMaleAdultBeltWeights(8, brown) + createMaleAdultBeltWeights(10, black)
        val femaleAdult = createFemaleAdultBeltWeights(5, white) + createFemaleAdultBeltWeights(6, blue) + createFemaleAdultBeltWeights(7, purple) + createFemaleAdultBeltWeights(8, brown) + createFemaleAdultBeltWeights(10, black)*/

        return Collections.emptyList()
    }
}