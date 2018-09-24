package compman.compsrv.validators

import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType

interface CategoryCommandValidator {
    fun validate(command: Command, categoryState: CategoryState?): List<String>

    fun getAffectedCommands(): List<CommandType>
}