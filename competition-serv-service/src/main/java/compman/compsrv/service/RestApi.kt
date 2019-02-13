package compman.compsrv.service

import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CategoryState
import compman.compsrv.jpa.competition.CompetitionDashboardState
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.CommonResponse
import compman.compsrv.model.PageResponse
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.model.dto.schedule.ScheduleDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.util.*

@RestController
@RequestMapping("/api/v1")
class RestApi(private val categoryGeneratorService: CategoryGeneratorService,
              private val stateQueryService: StateQueryService,
              private val producer: KafkaProducer<String, CommandDTO>) {

    companion object {
        private val log = LoggerFactory.getLogger(RestApi::class.java)
    }

    @RequestMapping("/command/{competitionId}", method = [RequestMethod.POST])
    fun sendCommand(@RequestBody command: CommandDTO, @PathVariable competitionId: String): CommonResponse {
        return try {
            log.info("Received a command: $command for competitionId: $competitionId")
            val correlationId = UUID.randomUUID().toString()
            producer.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, competitionId, command.setCorrelationId(correlationId)))
            CommonResponse(0, "", correlationId.toByteArray())
        } catch (e: Exception) {
            CommonResponse(500, "Exception: ${e.message}", null)
        }
    }


    @RequestMapping("/store/categorystate", method = [RequestMethod.GET])
    fun getCategoryState(@RequestParam("competitionId") competitionId: String, @RequestParam("categoryId") categoryId: String): CategoryState? = stateQueryService.getCategoryState(Base64.getDecoder().decode(competitionId).toString(StandardCharsets.UTF_8), Base64.getDecoder().decode(categoryId).toString(StandardCharsets.UTF_8))


    @RequestMapping("/store/competitors", method = [RequestMethod.GET])
    fun getCompetitors(@RequestParam("competitionId") competitionId: String,
                       @RequestParam("searchString") searchString: String?,
                       @RequestParam("pageSize") pageSize: Int?,
                       @RequestParam("pageNumber") pageNumber: Int?): PageResponse<CompetitorDTO> {
        val page = stateQueryService.getCompetitors(competitionId, searchString, pageSize ?: 50, pageNumber ?: 0)
        return PageResponse(competitionId, page.totalElements, page.number, page.content.mapNotNull { it.toDTO() }.toTypedArray())
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
    fun getCompetitionProperties(@RequestParam("competitionId") competitionId: String?) = stateQueryService.getCompetitionProperties(competitionId)

    @RequestMapping("/store/categories", method = [RequestMethod.GET])
    fun getCategories(@RequestParam("competitionId") competitionId: String) = stateQueryService.getCategories(competitionId)

    @RequestMapping("/store/brackets", method = [RequestMethod.GET])
    fun getBrackets(@RequestParam("competitionId") competitionId: String?, @RequestParam("categoryId") categoryId: String?): Array<BracketDescriptor> {
        return if (competitionId.isNullOrBlank() && categoryId.isNullOrBlank()) {
            log.warn("Competition id is missing.")
            emptyArray()
        } else {
            if (categoryId.isNullOrBlank()) {
                stateQueryService.getBracketsForCompetition(competitionId!!).toTypedArray()
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
    fun getDefaultCategories(@RequestParam("sportsId") sportsId: String?, @RequestParam("competitionId") competitionId: String?): List<CategoryDescriptorDTO> {
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