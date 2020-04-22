package compman.compsrv.kafka.streams.transformer

import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.service.processor.event.IEffects
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.slf4j.LoggerFactory

open class CompetitionCommandTransformer(competitionStateService: ICommandProcessingService<CommandDTO, EventDTO>,
                                         private val competitionStateRepository: CompetitionPropertiesDao,
                                         private val competitionStateResolver: CompetitionStateResolver,
                                         effects: IEffects,
                                         mapper: ObjectMapper)
    : AbstractCommandTransformer(competitionStateService, effects, mapper) {


    override fun initState(id: String, correlationId: String?, transactional: Boolean) {
        competitionStateResolver.resolveLatestCompetitionState(id, correlationId, transactional)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCommandTransformer::class.java)
    }

    override fun canExecuteCommand(command: CommandDTO?): List<String> {
        return when (command?.type) {
            CommandType.CREATE_COMPETITION_COMMAND -> if (competitionStateRepository.existsById(command.competitionId)) {
                log.warn("Competition ${command.competitionId} already exists.")
                listOf("Competition already exists.")
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }
}