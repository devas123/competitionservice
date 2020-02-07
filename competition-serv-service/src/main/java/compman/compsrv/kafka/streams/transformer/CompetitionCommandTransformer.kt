package compman.compsrv.kafka.streams.transformer

import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.slf4j.LoggerFactory

open class CompetitionCommandTransformer(competitionStateService: CompetitionStateService,
                                         private val competitionStateRepository: CompetitionPropertiesDao,
                                         private val competitionStateResolver: CompetitionStateResolver,
                                         mapper: ObjectMapper)
    : AbstractCommandTransformer(competitionStateService, mapper) {


    override fun initState(id: String, correlationId: String?) {
        competitionStateResolver.resolveLatestCompetitionState(id, correlationId)
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