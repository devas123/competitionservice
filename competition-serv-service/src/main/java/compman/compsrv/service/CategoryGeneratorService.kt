package compman.compsrv.service

import compman.compsrv.model.commands.payload.AdjacencyList
import compman.compsrv.model.commands.payload.AdjacencyListEntry
import compman.compsrv.model.dto.competition.*
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import java.util.*
import kotlin.collections.HashMap

internal data class AdjacencyListEntryWithLevelAndId(val id: Int, val entry: CategoryRestrictionDTO, val level: Int)

@Component
class CategoryGeneratorService {
    companion object {
        val bjj: CategoryRestrictionDTO = CategoryRestrictionDTO().setId(IDGenerator.uid()).setType(CategoryRestrictionType.Value).setName("Sport").setValue("BJJ")
        private fun weightRestriction(alias: String = "", maxValue: String, minValue: String = "0"): CategoryRestrictionDTO = CategoryRestrictionDTO()
                .setId(IDGenerator.hashString("Weight/$minValue/$maxValue/$alias"))
                .setType(CategoryRestrictionType.Range)
                .setName("Weight")
                .setMaxValue(maxValue)
                .setMinValue(minValue)
                .setAlias(alias)
                .setUnit("kg")

        private fun ageRestriction(alias: String = "", minValue: String, maxValue: String = minValue): CategoryRestrictionDTO = CategoryRestrictionDTO()
                .setId(IDGenerator.hashString("Age/$minValue/$maxValue/$alias"))
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

        @ExperimentalStdlibApi
        fun printCategory(cat: CategoryDescriptorDTO): String {
            val sv = StringBuilder()
            if (!cat.restrictions.isNullOrEmpty()) {
                cat.restrictions.forEach {
                    sv.append(it.alias ?: it.name)
                    if (!it.value.isNullOrBlank()) {
                        sv.append("=").append(it.value)
                    }
                    sv.append("/")
                }
                sv.deleteAt(sv.lastIndex)
            }
            return sv.toString()
        }

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

        val restrictions = listOf(
                bjj, male, female, white,
                gray, yellow, orange, green, blue,
                purple, brown, black, adult, junior1,
                junior2, junior3, juvenile1, juvenile2,
                master1, master2, master3, master4, master5,
                master6, mightyMite1, mightyMite2, mightyMite3,
                peeWee1, peeWee2, peeWee3, teen1, teen2, teen3,
                admrooster, admlightFeather, admfeather, admlight,
                admmiddle, admmediumHeavy, admheavy, admsuperHeavy,
                jmrooster, jmlightFeather, jmfeather, jmlight,
                jmmiddle, jmmediumHeavy, jmheavy, jmsuperHeavy,
                multraHeavy, openClass, adflightFeather, adffeather, adflight,
                adfmiddle, adfmediumHeavy, jflightFeather,
                jflightFeather, jffeather, jflight, jfmiddle, jfmediumHeavy, fheavy
        )

        fun createCategory(vararg restrictions: CategoryRestrictionDTO, registrationOpen: Boolean = true): CategoryDescriptorDTO {
            val restr = restrictions.mapIndexed { index, it -> it.setRestrictionOrder(index) }
            return CategoryDescriptorDTO()
                    .setRestrictions(restr.toTypedArray())
                    .setRegistrationOpen(registrationOpen)
                    .apply {
                        id = IDGenerator.categoryId(this)
                    }
        }
    }

    fun createDefaultBjjCategories(competitionId: String): List<CategoryDescriptorDTO> {

        fun createMaleAdultBeltWeights(belt: CategoryRestrictionDTO) = listOf(
                createCategory(bjj, adult, male, admrooster, belt),
                createCategory(bjj, adult, male, admlightFeather, belt),
                createCategory(bjj, adult, male, admfeather, belt),
                createCategory(bjj, adult, male, admlight, belt),
                createCategory(bjj, adult, male, admmiddle, belt),
                createCategory(bjj, adult, male, admmediumHeavy, belt),
                createCategory(bjj, adult, male, admheavy, belt),
                createCategory(bjj, adult, male, admsuperHeavy, belt),
                createCategory(bjj, adult, male, multraHeavy, belt)
        )

        fun createFemaleAdultBeltWeights(belt: CategoryRestrictionDTO) = listOf(
                createCategory(bjj, adult, female, admlightFeather, belt),
                createCategory(bjj, adult, female, admfeather, belt),
                createCategory(bjj, adult, female, admlight, belt),
                createCategory(bjj, adult, female, admmiddle, belt),
                createCategory(bjj, adult, female, admmediumHeavy, belt),
                createCategory(bjj, adult, female, admheavy, belt)
        )

        val maleAdult = createMaleAdultBeltWeights(white) + createMaleAdultBeltWeights(blue) + createMaleAdultBeltWeights(purple) + createMaleAdultBeltWeights(brown) + createMaleAdultBeltWeights(black)
        val femaleAdult = createFemaleAdultBeltWeights(white) + createFemaleAdultBeltWeights(blue) + createFemaleAdultBeltWeights(purple) + createFemaleAdultBeltWeights(brown) + createFemaleAdultBeltWeights(black)

        return maleAdult + femaleAdult
    }

    internal fun generateCategoriesFromRestrictions(competitionId: String,
                                                    restrictions: Array<out CategoryRestrictionDTO>,
                                                    idTree: AdjacencyList,
                                                    restrictionNamesOrder: Map<String, Int>): List<CategoryDescriptorDTO> {
        val stack = Stack<AdjacencyListEntryWithLevelAndId>()
        val idMap = HashMap<Int, AdjacencyListEntry>()
        idTree.vertices.forEach { v -> idMap[v.id] = v }
        stack.push(AdjacencyListEntryWithLevelAndId(idTree.root, restrictions[idTree.root], 0))
        val paths = mutableListOf<Array<CategoryRestrictionDTO>>()
        val currentPath = ArrayDeque<CategoryRestrictionDTO>()
        while (!stack.isEmpty()) {
            val node = stack.pop()
            while (currentPath.size > node.level) {
                currentPath.removeLast()
            }
            currentPath.add(node.entry)
            idMap[node.id]?.children?.forEach { stack.push(AdjacencyListEntryWithLevelAndId(it, restrictions[it], node.level + 1)) }
                    ?: paths.add(currentPath.sortedBy {categoryRestrictionDTO ->
                        restrictionNamesOrder.getValue(categoryRestrictionDTO.name) }.mapIndexed { index, c ->
                        c.setRestrictionOrder(index)
                    }.toTypedArray())
        }
        return paths.mapNotNull { path ->
            CategoryDescriptorDTO()
                    .setRegistrationOpen(false)
                    .setId(IDGenerator.uid())
                    .setRestrictions(path)
        }
    }
}