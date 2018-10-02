package compman.compsrv.validators

import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import org.springframework.stereotype.Component

@Component
class CategoryCommandsValidatorRegistry(private val validators: List<CategoryCommandValidator>) {

    fun validate(command: Command?, categoryState: CategoryState?): List<String> {
        val result = mutableListOf<String>()
        if (command == null) {
            result.add("Command is null.")
        }
        if (categoryState == null && command?.type != CommandType.INIT_CATEGORY_STATE_COMMAND) {
            result.add("Category state is null.")
        }
        if (command != null) {
            when (command.type) {
                CommandType.INIT_CATEGORY_STATE_COMMAND -> if (categoryState != null) result.add("Category state already initialized.")
                else -> result.addAll(validators.filter { it.getAffectedCommands().contains(command.type) }
                        .map { it.validate(command, categoryState) }
                        .fold(emptyList()) { acc, b -> acc + b })
            }
        }
        return result
    }


}