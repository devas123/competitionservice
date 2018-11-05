package compman.compsrv.kafka.streams

import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.service.DashboardStateService

class MatGlobalCommandExecutorTransformer(stateStoreName: String, dashboardStateService: DashboardStateService) : StateForwardingValueTransformer<CompetitionDashboardState>(stateStoreName, CompetitionServiceTopics.DASHBOARD_STATE_CHANGELOG_TOPIC_NAME, dashboardStateService) {
    override fun getStateKey(command: Command?) = command?.competitionId!!

    override fun canExecuteCommand(state: CompetitionDashboardState?, command: Command?): List<String> {
        /*(state == null || state.eventOffset < context.offset()) &&*/
        return emptyList()
    }
}