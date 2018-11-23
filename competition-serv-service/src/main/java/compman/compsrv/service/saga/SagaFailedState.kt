package compman.compsrv.service.saga

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder

data class SagaFailedState(private val compensatingActions: List<Command>) : SagaState {
    override fun compensatingActions(): List<Command> = compensatingActions

    override fun nextStep(): List<Command> = compensatingActions

    override fun completed(): Boolean = true

    override fun nextState(event: EventHolder): SagaState = this
}