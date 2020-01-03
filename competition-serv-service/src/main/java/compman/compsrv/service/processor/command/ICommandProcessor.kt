package compman.compsrv.service.processor.command

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO

interface ICommandProcessor<State> {
    fun affectedCommands(): Set<CommandType>
    fun executeCommand(state: State, command: CommandDTO): List<EventDTO>
}