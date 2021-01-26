package compman.compsrv.service.processor.category.command

import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddCategoryPayload
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.Rules
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_COMMAND_EXECUTORS)
class AddCategory(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : ICommandExecutor<Category>, ValidatedCommandExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        executeValidated<AddCategoryPayload>(command) { payload, _ ->
            val c = payload.category
            if (c?.restrictions?.isNullOrEmpty() == false) {
                val restrictionsValid = Rules.accumulateErrors {
                    c.restrictions.map { it.validate() }
                }.map { it.fix() }
                val categoryId = command.categoryId
                    ?: IDGenerator.hashString("${command.competitionId}/${IDGenerator.categoryId(c)}")
                if (!dbOperations.categoryExists(categoryId)) {
                    if (restrictionsValid.all { restriction -> restriction.isValid }) {
                        val registrationOpen = c.registrationOpen ?: true
                        val state = c
                            .setRestrictions(c.restrictions.map { it.withId() }.toTypedArray())
                            .setId(categoryId).setRegistrationOpen(registrationOpen)
                        Category(categoryId, state) to listOf(
                            AbstractAggregateService.createEvent(
                                command,
                                EventType.CATEGORY_ADDED,
                                CategoryAddedPayload(state)
                            ).apply { this.categoryId = categoryId }
                        )
                    } else {
                        throw IllegalArgumentException(restrictionsValid.fold(StringBuilder()) { acc, r ->
                            acc.append(
                                r.fold(
                                    { it.toList().joinToString(",") },
                                    { "" })
                            )
                        }.toString())
                    }
                } else {
                    throw IllegalArgumentException("Category with ID $categoryId already exists.")
                }
            } else {
                throw IllegalArgumentException("Failed to get category from command payload")
            }
        }.unwrap(command)

    private fun CategoryRestrictionDTO.withId(): CategoryRestrictionDTO = this.setId(IDGenerator.restrictionId(this))


    override val commandType: CommandType
        get() = CommandType.ADD_CATEGORY_COMMAND


}