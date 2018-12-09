package compman.compsrv.service.saga

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder

interface Saga {
    fun init(state: SagaState)
    fun getState(): SagaState
    fun getCommands(): List<Command>
    fun completed(): Boolean
    fun acceptEvent(eventHolder: EventHolder)
    fun getAllCompensatingActions(): List<Command>
}