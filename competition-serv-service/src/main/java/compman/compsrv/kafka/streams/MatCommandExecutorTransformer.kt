package compman.compsrv.kafka.streams

import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.MatState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.service.MatStateService
import compman.compsrv.validators.MatCommandsValidatorRegistry

class MatCommandExecutorTransformer(stateStoreName: String,
                                    private val validators: MatCommandsValidatorRegistry,
                                    matStateService: MatStateService) : StateForwardingValueTransformer<MatState>(stateStoreName, CompetitionServiceTopics.MAT_STATE_CHANGELOG_TOPIC_NAME, matStateService) {
    override fun getStateKey(command: Command?) = command?.matId!!




    override fun canExecuteCommand(state: MatState?, command: Command?): List<String> {
        /*(state == null || state.eventOffset < context.offset()) &&*/
        return validators.validate(command, state)
    }
}