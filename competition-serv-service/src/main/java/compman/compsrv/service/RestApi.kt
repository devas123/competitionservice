package compman.compsrv.service

import compman.compsrv.cluster.ClusterMember
import compman.compsrv.model.CommonResponse
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
@RequestMapping("/api/v1")
class RestApi(private val stateQueryService: StateQueryService,
              private val commandProducer: CommandProducer) {

    fun String?.isNullOrEmptyOrUndefined() = this.isNullOrEmpty() || this == "null" || this == "undefined"

    companion object {
        private val log = LoggerFactory.getLogger(RestApi::class.java)
    }

    @RequestMapping("/store/fightsbycategories", method = [RequestMethod.GET])
    fun getFightIdsByCategoryIds(@RequestParam("competitionId") competitionId: String): Map<String, Array<String>> {
        return stateQueryService.getFightIdsByCategoryIds(competitionId)
    }

    @RequestMapping(path = ["/command/{competitionId}", "/command"], method = [RequestMethod.POST])
    fun sendCommand(@RequestBody command: CommandDTO, @PathVariable competitionId: String?): ResponseEntity<CommonResponse> {
        log.info("COMMAND ASYNC: $command")
        return  kotlin.runCatching {
            val response = commandProducer.sendCommandAsync(command, competitionId)
            ResponseEntity(response, HttpStatus.resolve(response.status) ?: HttpStatus.OK)
        }.getOrElse { e ->
            ResponseEntity(CommonResponse(500, "Exception: ${e.message}", null), HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    @RequestMapping(path = ["/commandsync/{competitionId}", "/commandsync"], method = [RequestMethod.POST])
    fun sendCommandSync(@RequestBody command: CommandDTO, @PathVariable competitionId: String?): ResponseEntity<Array<out EventDTO>> {
        log.info("COMMAND SYNC: $command")
        return ResponseEntity(commandProducer.sendCommandSync(command, competitionId), HttpStatus.OK)
    }


    @RequestMapping(path = ["/cluster/info"], method = [RequestMethod.GET])
    fun getClusterInfo(): Array<ClusterMember> = stateQueryService.getClusterInfo()


    @RequestMapping("/store/categorystate", method = [RequestMethod.GET])
    fun getCategoryState(@RequestParam("competitionId") competitionId: String, @RequestParam("categoryId") categoryId: String): Any? {
        return stateQueryService.getCategoryState(
                competitionId, categoryId)
    }


    @RequestMapping("/store/mats", method = [RequestMethod.GET])
    fun getMats(@RequestParam("competitionId") competitionId: String, @RequestParam("periodId") periodId: String): List<MatDescriptionDTO> {
        return stateQueryService.getMats(competitionId, periodId)?.toList().orEmpty()
    }

    @RequestMapping("/store/matfights", method = [RequestMethod.GET])
    fun getMatFightsWithCompetitors(@RequestParam("competitionId") competitionId: String, @RequestParam("matId") matId: String, @RequestParam("maxResults") maxResults: Long?): FightsWithCompetitors? {
        return stateQueryService.getMatFights(competitionId, matId, maxResults ?: 100L)
    }

    @RequestMapping("/store/stagefights", method = [RequestMethod.GET])
    fun getStageFights(@RequestParam("competitionId") competitionId: String, @RequestParam("stageId") stageId: String): List<FightDescriptionDTO> {
        return stateQueryService.getStageFights(competitionId, stageId)?.toList().orEmpty()
    }

    @RequestMapping("/store/fight", method = [RequestMethod.GET])
    fun getFight(@RequestParam("competitionId") competitionId: String, @RequestParam("fightId") fightId: String): FightDescriptionDTO? {
        return stateQueryService.getFight(competitionId, fightId)
    }

    @RequestMapping("/store/stages", method = [RequestMethod.GET])
    fun getCategoryStages(@RequestParam("competitionId") competitionId: String, @RequestParam("categoryId") categoryId: String): Array<StageDescriptorDTO> {
        return stateQueryService.getStages(competitionId, categoryId) ?: emptyArray()
    }

    @RequestMapping("/store/competitors", method = [RequestMethod.GET])
    fun getCompetitors(@RequestParam("competitionId") competitionId: String,
                       @RequestParam("categoryId") categoryId: String?,
                       @RequestParam("searchString") searchString: String?,
                       @RequestParam("pageSize") pageSize: Int?,
                       @RequestParam("pageNumber") pageNumber: Int?): Any? {
        return stateQueryService.getCompetitors(competitionId, categoryId, searchString, pageSize ?: 50, pageNumber
                ?: 0)
    }


    @RequestMapping("/store/competitor", method = [RequestMethod.GET])
    fun getCompetitor(@RequestParam("competitionId") competitionId: String,
                      @RequestParam("fighterId") fighterId: String): CompetitorDTO? {
        if (competitionId == "null" || competitionId.isEmpty() || fighterId == "null" || fighterId.isEmpty()) {
            return null
        }
        return stateQueryService.getCompetitor(competitionId, fighterId)
    }
    @RequestMapping("/store/fightresultoptions", method = [RequestMethod.GET])
    fun getFightResultOptions(@RequestParam("competitionId") competitionId: String,
                      @RequestParam("fightId") fightId: String): Array<FightResultOptionDTO>? {
        if (competitionId.isNullOrEmptyOrUndefined() || fightId.isNullOrEmptyOrUndefined()) {
            return null
        }
        return stateQueryService.getFightResultOptions(competitionId, fightId)
    }

    @RequestMapping("/store/comprops", method = [RequestMethod.GET])
    fun getCompetitionProperties(@RequestParam("competitionId") competitionId: String?): CompetitionPropertiesDTO? {
        log.info("looking for the competition properties for competition $competitionId")
        return competitionId?.let {
            stateQueryService.getCompetitionProperties(it).block(Duration.ofMillis(10000)) }?.orNull()
    }
    @RequestMapping("/store/reginfo", method = [RequestMethod.GET])
    fun getRegistrationInfo(@RequestParam("competitionId") competitionId: String?): RegistrationInfoDTO? {
        log.info("looking for the competition properties for competition $competitionId")
        return competitionId?.let {
            stateQueryService.getRegistrationInfo(it).block(Duration.ofMillis(10000)) }
    }

    @RequestMapping("/store/infotemplate", method = [RequestMethod.GET])
    fun getCompetitionInfo(@RequestParam("competitionId") competitionId: String?) = stateQueryService.getCompetitionInfoTemplate(competitionId)

    @RequestMapping("/store/categories", method = [RequestMethod.GET])
    fun getCategories(@RequestParam("competitionId") competitionId: String) = stateQueryService.getCategories(competitionId)


    @RequestMapping("/store/schedule", method = [RequestMethod.GET])
    fun getSchedule(@RequestParam("competitionId") competitionId: String?): ScheduleDTO? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition id is missing.")
            null
        } else {
            stateQueryService.getSchedule(competitionId)
        }
    }

    @RequestMapping("/store/defaultrestrictions", method = [RequestMethod.GET])
    fun getDefaultCategories(@RequestParam("sportsId") sportsId: String?, includeKids: Boolean? = false): List<CategoryRestrictionDTO> {
        return if (sportsId.isNullOrBlank()) {
            log.warn("Sports id is missing.")
            emptyList()
        } else {
            CategoryGeneratorService.restrictions
        }
    }

    @RequestMapping("/store/defaultfightresults", method = [RequestMethod.GET])
    fun getDefaultFightResults(@RequestParam("sportsId") sportsId: String?, @RequestParam("competitionId") competitionId: String?, includeKids: Boolean? = false): List<FightResultOptionDTO> {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Sports id is $sportsId, competition ID is $competitionId.")
            emptyList()
        } else {
            FightResultOptionDTO.values
        }
    }


    @RequestMapping("/store/dashboardstate", method = [RequestMethod.GET])
    fun getDashboardState(@RequestParam("competitionId") competitionId: String?): CompetitionDashboardStateDTO? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition ID is $competitionId.")
            null
        } else {
            stateQueryService.getDashboardState(competitionId)
        }
    }
}