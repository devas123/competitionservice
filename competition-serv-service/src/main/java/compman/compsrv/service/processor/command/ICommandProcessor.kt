package compman.compsrv.service.processor.command

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO

interface ICommandProcessor {
    fun affectedCommands(): Set<CommandType>
    fun executeCommand(command: CommandDTO): List<EventDTO>
}