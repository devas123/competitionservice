package compman.compsrv.service

import compman.compsrv.model.commands.payload.AdjacencyList
import compman.compsrv.model.commands.payload.AdjacencyListEntry
import org.junit.Test
import org.slf4j.LoggerFactory

@ExperimentalStdlibApi
class CategoryGeneratorServiceTest {
    private val log = LoggerFactory.getLogger(CategoryGeneratorServiceTest::class.java)
    @Test
    fun testGenerateCategories() {
        val categoryGeneratorService = CategoryGeneratorService()
        val restrictions = arrayOf(
                CategoryGeneratorService.male, //0
                CategoryGeneratorService.master1, //1
                CategoryGeneratorService.adult, //2
                CategoryGeneratorService.adffeather, //3
                CategoryGeneratorService.brown, //4
                CategoryGeneratorService.white, //5
                CategoryGeneratorService.female //6
        )
        val idTree1 = AdjacencyList(2, arrayOf(AdjacencyListEntry(2, arrayOf(0, 6).toIntArray()),
                AdjacencyListEntry(0, arrayOf(4,5).toIntArray()), AdjacencyListEntry(6, arrayOf(4,5).toIntArray())))
        val idTree2 = AdjacencyList(1, arrayOf(AdjacencyListEntry(1, arrayOf(0).toIntArray()),
                AdjacencyListEntry(0, arrayOf(4,5).toIntArray())))

        val restrNamesOrder = mapOf("Gender" to 0, "Age" to 1, "Belt" to 2)

        val categories = categoryGeneratorService.generateCategoriesFromRestrictions("test", restrictions, idTree1, restrNamesOrder)
        log.info("\n" + categories.joinToString("\n") { CategoryGeneratorService.printCategory(it) })
        val categories2 = categoryGeneratorService.generateCategoriesFromRestrictions("test", restrictions, idTree2, restrNamesOrder)
        log.info("\n" + categories2.joinToString("\n") { CategoryGeneratorService.printCategory(it) })
    }
}