package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.CommonResponse
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.util.IDGenerator
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class CommandProducer(private val producer: KafkaProducer<String, CommandDTO>,
                      private val mapper: ObjectMapper) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(CommandProducer::class.java)
        fun createSendProcessingInfoCommand(competitionId: String): CommandDTO = CommandDTO().setCompetitionId(competitionId).setType(CommandType.SEND_PROCESSING_INFO_COMMAND)
    }

    fun sendCommand(command: CommandDTO, competitionId: String?): CommonResponse {
        return try {
            val correlationId = UUID.randomUUID().toString()
            if (command.type == CommandType.CREATE_COMPETITION_COMMAND) {
                log.info("Received a create competition command: $command")
                val payload = mapper.convertValue(command.payload, CreateCompetitionPayload::class.java)
                if (payload?.properties?.competitionName.isNullOrBlank()) {
                    log.error("Empty competition name, skipping create command")
                    CommonResponse(400, "Empty competition name, skipping create command", correlationId.toByteArray())
                } else {
                    val id = IDGenerator.hashString(payload.properties.competitionName)
                    producer.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, id, command.setCorrelationId(correlationId).setCompetitionId(id)))
                    CommonResponse(0, "", correlationId.toByteArray())
                }
            } else {
                log.info("Received a command: $command for competitionId: $competitionId")
                producer.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, competitionId, command.setCorrelationId(correlationId)))
                CommonResponse(0, "", correlationId.toByteArray())
            }
        } catch (e: Exception) {
            CommonResponse(500, "Exception: ${e.message}", null)
        }
    }
}