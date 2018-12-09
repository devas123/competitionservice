package compman.compsrv.service.saga

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder

interface SagaState {
    fun compensatingActions(): List<Command>
    fun nextStep(): List<Command>
    fun completed(): Boolean
    fun nextState(event: EventHolder): SagaState
}