package compman.compsrv.service

import java.math.BigDecimal

open class AbstractGenerateServiceTest {
    protected val duration: BigDecimal = BigDecimal.valueOf(8)
    protected val competitionId = "UG9wZW5nYWdlbiBPcGVu"
    protected val categoryId = "UG9wZW5nYWdlbiBPcGVu-UG9wZW5nYWdlbiBPcGVu"
    protected val stageId = "asoifjqwoijqwoijqpwtoj2j12-j1fpasoj"
    val category = CategoryGeneratorService.createCategory(8, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown)
}