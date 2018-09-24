package compman.compsrv.validators

import compman.compsrv.model.competition.MatState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType

interface MatCommandValidator {
    fun validate(command: Command, matState: MatState?): List<String>

    fun getAffectedCommands(): List<CommandType>
}