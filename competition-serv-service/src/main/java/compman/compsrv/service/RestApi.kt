package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.hash.Hashing
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CompetitionDashboardState
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.CommonResponse
import compman.compsrv.model.PageResponse
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.model.dto.schedule.ScheduleDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.util.*

@RestController
@RequestMapping("/api/v1")
class RestApi(private val categoryGeneratorService: CategoryGeneratorService,
              private val stateQueryService: StateQueryService,
              private val producer: KafkaProducer<String, CommandDTO>,
              private val mapper: ObjectMapper) {

    companion object {
        private val log = LoggerFactory.getLogger(RestApi::class.java)
    }

    @RequestMapping(path = ["/command/{competitionId}", "/command"], method = [RequestMethod.POST])
    fun sendCommand(@RequestBody command: CommandDTO, @PathVariable competitionId: String?): ResponseEntity<CommonResponse>  {
        return try {
            val correlationId = UUID.randomUUID().toString()
            if (command.type == CommandType.CREATE_COMPETITION_COMMAND) {
                log.info("Received a create competition command: $command")
                val payload = mapper.convertValue(command.payload, CreateCompetitionPayload::class.java)
                if (payload?.properties?.competitionName.isNullOrBlank()) {
                    log.error("Empty competition name, skipping create command")
                    ResponseEntity(CommonResponse(400, "Empty competition name, skipping create command", correlationId.toByteArray()), HttpStatus.BAD_REQUEST)
                } else {
                    val id = Hashing.sha256().hashBytes(payload.properties.competitionName.toByteArray(StandardCharsets.UTF_8)).toString()
                    producer.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, id, command.setCorrelationId(correlationId).setCompetitionId(id)))
                    ResponseEntity(CommonResponse(0, "", correlationId.toByteArray()), HttpStatus.OK)
                }
            } else {
                log.info("Received a command: $command for competitionId: $competitionId")
                producer.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, competitionId, command.setCorrelationId(correlationId)))
                ResponseEntity(CommonResponse(0, "", correlationId.toByteArray()), HttpStatus.OK)
            }
        } catch (e: Exception) {
            ResponseEntity(CommonResponse(500, "Exception: ${e.message}", null), HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }


    @RequestMapping("/store/categorystate", method = [RequestMethod.GET])
    fun getCategoryState(@RequestParam("competitionId") competitionId: String, @RequestParam("categoryId") categoryId: String, @RequestParam("internal") internal: Boolean? = false): Any? {
        val categoryState = stateQueryService.getCategoryState(
                Base64.getDecoder().decode(competitionId).toString(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(categoryId).toString(StandardCharsets.UTF_8))
        return if (internal == true) {
            categoryState
        } else {
            categoryState?.toDTO()
        }
    }


    @RequestMapping("/store/competitors", method = [RequestMethod.GET])
    fun getCompetitors(@RequestParam("competitionId") competitionId: String,
                       @RequestParam("categoryId") categoryId: String?,
                       @RequestParam("searchString") searchString: String?,
                       @RequestParam("pageSize") pageSize: Int?,
                       @RequestParam("pageNumber") pageNumber: Int?,
                       @RequestParam("internal") internal: Boolean? = false): Any? {
        val page = stateQueryService.getCompetitors(competitionId, categoryId, searchString, pageSize ?: 50, pageNumber ?: 0)
        return if (internal == true) {
            page
        } else {
            PageResponse(competitionId, page?.totalElements ?: 0, page?.number ?: 0, page?.content?.mapNotNull { it.toDTO() }?.toTypedArray() ?: emptyArray())
        }
    }


    @RequestMapping("/store/competitor", method = [RequestMethod.GET])
    fun getCompetitor(@RequestParam("competitionId") competitionId: String,
                      @RequestParam("categoryId") categoryId: String,
                      @RequestParam("fighterId") competitorId: String): CompetitorDTO? {
        if (competitionId == "null" || competitionId.isEmpty() || competitorId == "null" || competitorId.isEmpty()) {
            return null
        }
        val decodedFighterId = String(Base64.getDecoder().decode(competitorId))
        val decodedCategoryId = String(Base64.getDecoder().decode(categoryId))
        val decodedCompetitionId = String(Base64.getDecoder().decode(competitionId))
        return stateQueryService.getCompetitor(decodedCompetitionId, decodedCategoryId, decodedFighterId).orElse(null).toDTO()
    }

    @RequestMapping("/store/comprops", method = [RequestMethod.GET])
    fun getCompetitionProperties(@RequestParam("competitionId") competitionId: String?): CompetitionPropertiesDTO? {
        val k = stateQueryService.getCompetitionProperties(competitionId)
        return k
    }
    @RequestMapping("/store/competitionstate", method = [RequestMethod.GET])
    fun getCompetitionState(@RequestParam("competitionId") competitionId: String?) = stateQueryService.getCompetitionState(competitionId)?.toDTO()

    @RequestMapping("/store/categories", method = [RequestMethod.GET])
    fun getCategories(@RequestParam("competitionId") competitionId: String) = stateQueryService.getCategories(competitionId)

    @RequestMapping("/store/brackets", method = [RequestMethod.GET])
    fun getBrackets(@RequestParam("competitionId") competitionId: String?, @RequestParam("categoryId") categoryId: String?): Array<BracketDescriptor> {
        return if (competitionId.isNullOrBlank() && categoryId.isNullOrBlank()) {
            log.warn("Competition id is missing.")
            emptyArray()
        } else {
            if (categoryId.isNullOrBlank()) {
                stateQueryService.getBracketsForCompetition(competitionId!!)?.toTypedArray() ?: emptyArray()
            } else {
                stateQueryService.getBrackets(categoryId).map { arrayOf(it) }.orElse(emptyArray())
            }
        }
    }


    @RequestMapping("/store/schedule", method = [RequestMethod.GET])
    fun getSchedule(@RequestParam("competitionId") competitionId: String?): ScheduleDTO? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition id is missing.")
            null
        } else {
            stateQueryService.getSchedule(competitionId)?.toDTO()
        }
    }

    @RequestMapping("/store/defaultcategories", method = [RequestMethod.GET])
    fun getDefaultCategories(@RequestParam("sportsId") sportsId: String?, @RequestParam("competitionId") competitionId: String?, includeKids: Boolean? = false): List<CategoryDescriptorDTO> {
        return if (sportsId.isNullOrBlank() || competitionId.isNullOrBlank()) {
            log.warn("Sports id is $sportsId, competition ID is $competitionId.")
            emptyList()
        } else {
            categoryGeneratorService.createDefaultBjjCategories(competitionId)
        }
    }

    @RequestMapping("/store/dashboardstate", method = [RequestMethod.GET])
    fun getDashboardState(@RequestParam("competitionId") competitionId: String?): CompetitionDashboardState? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition ID is $competitionId.")
            null
        } else {
            stateQueryService.getDashboardState(competitionId)
        }
    }

    private fun fightReady(fight: FightDescription) = fight.stage != FightStage.FINISHED && fight.scores.isNotEmpty() && fight.scores.all { Schedule.compNotEmpty(it.competitor) }
}