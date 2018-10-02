package compman.compsrv.validators

import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AddCategoryValidator : CategoryCommandValidator {
    companion object {
        private val log = LoggerFactory.getLogger(AddCategoryValidator::class.java)
    }

    override fun validate(command: Command, categoryState: CategoryState?): List<String> {
        val result = mutableListOf<String>()
        if (command.payload?.get("email") == null) {
            log.warn("Email missing for competitor.")
            result.add("Email missing for competitor.")
        }
        if (categoryState?.competitors?.any { it.email == command.payload?.get("email") } == true) {
            log.warn("Competitor already exists.")
            result.add("Competitor already exists.")
        }
        return result
    }

    override fun getAffectedCommands(): List<CommandType> {
        return listOf(CommandType.ADD_COMPETITOR_COMMAND)
    }

}