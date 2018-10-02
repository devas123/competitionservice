package compman.compsrv.validators

import compman.compsrv.model.competition.MatState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import org.springframework.stereotype.Component

@Component
class MatCommandsValidatorRegistry(private val validators: List<MatCommandValidator> = emptyList()) {

    fun validate(command: Command?, matState: MatState?): List<String> {
        val result = mutableListOf<String>()
        if (command == null) {
            result.add("Command is null.")
            return result
        }
        if (command.matId.isNullOrBlank()) {
            result.add("Mat ID is null or blank")
        }
        if (matState == null && command.type != CommandType.INIT_MAT_STATE_COMMAND) {
            result.add("Mat state is null.")
        }
        result.addAll(validators.filter { it.getAffectedCommands().contains(command.type) }
                .map { it.validate(command, matState) }
                .fold(emptyList()) { acc, b -> acc + b })
        return result
    }


}