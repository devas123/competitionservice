package compman.compsrv.kafka.streams

import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.service.ICommandProcessingService

class CategoryCommandExecutorTransformer(stateStoreName: String, categoryStateService: ICommandProcessingService<CategoryState, Command, EventHolder>) : StateForwardingValueTransformer<CategoryState>(stateStoreName, CompetitionServiceTopics.CATEGORY_STATE_CHANGELOG_TOPIC_NAME, categoryStateService) {
    override fun getStateKey(command: Command?): String = command?.categoryId ?: throw IllegalArgumentException("Category Id not provided. $command")
}