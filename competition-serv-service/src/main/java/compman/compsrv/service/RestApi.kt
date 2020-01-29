package compman.compsrv.service

import compman.compsrv.cluster.ClusterMember
import compman.compsrv.model.CommonResponse
import compman.compsrv.model.PageResponse
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatStateDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.events.EventDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class RestApi(private val categoryGeneratorService: CategoryGeneratorService,
              private val stateQueryService: StateQueryService,
              private val clusterInfoService: ClusterInfoService,
              private val commandProducer: CommandProducer) {

    companion object {
        private val log = LoggerFactory.getLogger(RestApi::class.java)
    }

    @RequestMapping(path = ["/command/{competitionId}", "/command"], method = [RequestMethod.POST])
    fun sendCommand(@RequestBody command: CommandDTO, @PathVariable competitionId: String?): ResponseEntity<CommonResponse> {
        return try {
            val response = commandProducer.sendCommandAsync(command, competitionId)
            ResponseEntity(response, HttpStatus.resolve(response.status) ?: HttpStatus.OK)
        } catch (e: Exception) {
            ResponseEntity(CommonResponse(500, "Exception: ${e.message}", null), HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    @RequestMapping(path = ["/commandsync/{competitionId}", "/commandsync"], method = [RequestMethod.POST])
    fun sendCommandSync(@RequestBody command: CommandDTO, @PathVariable competitionId: String?): ResponseEntity<Array<EventDTO>> {
        return ResponseEntity(commandProducer.sendCommandSync(command, competitionId), HttpStatus.OK)
    }


    @RequestMapping(path = ["/cluster/info"], method = [RequestMethod.GET])
    fun getClusterInfo(): Array<ClusterMember> = clusterInfoService.getClusterInfo()


    @RequestMapping("/store/categorystate", method = [RequestMethod.GET])
    fun getCategoryState(@RequestParam("competitionId") competitionId: String, @RequestParam("categoryId") categoryId: String): Any? {
        val state = stateQueryService.getCategoryState(
                competitionId, categoryId)
        return state?.setBrackets(state.brackets?.setStages(state.brackets.stages))
    }

    @RequestMapping("/store/mats", method = [RequestMethod.GET])
    fun getMats(@RequestParam("competitionId") competitionId: String, @RequestParam("periodId") periodId: String): List<MatStateDTO> {
        return stateQueryService.getMats(competitionId, periodId)?.toList() ?: emptyList()
    }

    @RequestMapping("/store/matfights", method = [RequestMethod.GET])
    fun getMatFights(@RequestParam("competitionId") competitionId: String, @RequestParam("matId") matId: String, @RequestParam("maxResults") maxResults: Int): List<FightDescriptionDTO> {
        return stateQueryService.getMatFights(competitionId, matId, maxResults)?.toList() ?: emptyList()
    }

    @RequestMapping("/store/stagefights", method = [RequestMethod.GET])
    fun getStageFights(@RequestParam("competitionId") competitionId: String, @RequestParam("stageId") stageId: String): List<FightDescriptionDTO> {
        return stateQueryService.getStageFights(competitionId, stageId)?.toList() ?: emptyList()
    }

    @RequestMapping("/store/competitors", method = [RequestMethod.GET])
    fun getCompetitors(@RequestParam("competitionId") competitionId: String,
                       @RequestParam("categoryId") categoryId: String?,
                       @RequestParam("searchString") searchString: String?,
                       @RequestParam("pageSize") pageSize: Int?,
                       @RequestParam("pageNumber") pageNumber: Int?): Any? {
        val page = stateQueryService.getCompetitors(competitionId, categoryId, searchString, pageSize ?: 50, pageNumber
                ?: 0)
        return PageResponse(competitionId, page?.totalElements ?: 0, (page?.number
                ?: 0) + 1, page?.content?.toTypedArray() ?: emptyArray())
    }


    @RequestMapping("/store/competitor", method = [RequestMethod.GET])
    fun getCompetitor(@RequestParam("competitionId") competitionId: String,
                      @RequestParam("fighterId") fighterId: String): CompetitorDTO? {
        if (competitionId == "null" || competitionId.isEmpty() || fighterId == "null" || fighterId.isEmpty()) {
            return null
        }
        return stateQueryService.getCompetitor(competitionId, fighterId)
    }

    @RequestMapping("/store/comprops", method = [RequestMethod.GET])
    fun getCompetitionProperties(@RequestParam("competitionId") competitionId: String?): CompetitionPropertiesDTO? {
        log.info("looking for the competition properties for competition $competitionId")
        return stateQueryService.getCompetitionProperties(competitionId)
    }

    @RequestMapping("/store/competitionstate", method = [RequestMethod.GET])
    fun getCompetitionState(@RequestParam("competitionId") competitionId: String?) = stateQueryService.getCompetitionState(competitionId)

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
    fun getDashboardState(@RequestParam("competitionId") competitionId: String?): CompetitionDashboardStateDTO? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition ID is $competitionId.")
            null
        } else {
            stateQueryService.getDashboardState(competitionId)
        }
    }
}