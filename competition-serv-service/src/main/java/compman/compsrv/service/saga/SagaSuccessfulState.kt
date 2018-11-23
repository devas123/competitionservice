package compman.compsrv.service.saga

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder

data class SagaSuccessfulState(private val command: Command): SagaState {
    override fun compensatingActions(): List<Command> = emptyList()

    override fun nextStep(): List<Command> = listOf(command)

    override fun completed(): Boolean = true

    override fun nextState(event: EventHolder): SagaState = this
}