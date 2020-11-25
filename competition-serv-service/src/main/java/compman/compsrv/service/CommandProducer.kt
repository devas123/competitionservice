package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.CommonResponse
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.toMonoOrEmpty
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CommandProducer(private val commandKafkaTemplate: KafkaTemplate<String, CommandDTO>,
                      private val mapper: ObjectMapper,
                      private val stateQueryService: StateQueryService,
                      private val commandSyncExecutor: CommandSyncExecutor) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(CommandProducer::class.java)
        fun createSendProcessingInfoCommand(competitionId: String, correlationId: String): CommandDTO =
                CommandDTO().setCorrelationId(correlationId).setCompetitionId(competitionId)
                        .setType(CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND).setId(IDGenerator.uid())
    }

    fun sendCommandAsync(command: CommandDTO, competitionId: String?, correlationId: String = IDGenerator.uid()): CommonResponse {
        return try {
            command.id = command.id ?: IDGenerator.uid()
            command.correlationId = correlationId
            if (command.type == CommandType.CREATE_COMPETITION_COMMAND) {
                log.info("Received a create competition command: $command")
                val payload = mapper.convertValue(command.payload, CreateCompetitionPayload::class.java)
                if (payload?.properties?.competitionName.isNullOrBlank()) {
                    log.error("Empty competition name, skipping create command")
                    CommonResponse(400, "Empty competition name, skipping create command", correlationId.toByteArray())
                } else {
                    val id = IDGenerator.hashString(payload.properties.competitionName)
                    commandKafkaTemplate.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, id, command.setCorrelationId(correlationId).setCompetitionId(id)))
                    CommonResponse(0, "", correlationId.toByteArray())
                }
            } else {
                log.info("Received a command: $command for competitionId: $competitionId")
                commandKafkaTemplate.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, competitionId, command.setCorrelationId(correlationId)))
                CommonResponse(0, "", correlationId.toByteArray())
            }
        } catch (e: Exception) {
            log.error("Error while executing async command", e)
            CommonResponse(500, "Exception: ${e.message}", null)
        }
    }

    fun sendCommandSync(command: CommandDTO, competitionId: String?): Array<out EventDTO> {
        fun createErrorEvent(errorMsg: String) = arrayOf(mapper.createErrorEvent(command, errorMsg))
        command.id = command.id ?: IDGenerator.uid()
        if (competitionId.isNullOrBlank()) {
            //this is a global command, can process anywhere
            //TODO
            sendCommandAsync(command, competitionId)
            return emptyArray()
        } else {
            val correlationId = IDGenerator.uid()
            return kotlin.runCatching {
                stateQueryService.localOrRemote(competitionId,
                        {
                            commandSyncExecutor.executeCommand(correlationId) {
                                sendCommandAsync(command, competitionId, correlationId)
                            }
                        },
                        { _, restTemplate, prefix ->
                            restTemplate.postForObject("$prefix/api/v1/commandsync", command, Array<EventDTO>::class.java, mapOf("competitionId" to competitionId)).toMonoOrEmpty()
                        })
            }
                    .recover { e ->
                        log.error("Error in sync command.", e)
                        createErrorEvent(e.message ?: "")
                    }.getOrDefault(emptyArray())!!
        }
    }
}