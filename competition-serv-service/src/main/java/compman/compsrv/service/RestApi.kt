package compman.compsrv.service

import compman.compsrv.cluster.PageResponse
import compman.compsrv.kafka.HostStoreInfo
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.competition.*
import compman.compsrv.model.dto.MatDTO
import compman.compsrv.model.schedule.Schedule
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.min

@RestController
@RequestMapping("/cluster")
class RestApi(private val categoryGeneratorService: CategoryGeneratorService,
              private val stateQueryService: StateQueryService) {

    companion object {
        private val log = LoggerFactory.getLogger(RestApi::class.java)
    }


    private fun createPageResponse(competitionId: String, pageSize: Int?, pageNumber: Int?, competitors: Array<Competitor>, total: Int): PageResponse<Competitor> {
        val sortedComps = competitors.sortedBy { it.firstName.toLowerCase() + it.lastName.toLowerCase() }
        return if (pageNumber != null && pageNumber > 1) {
            if (pageSize != null && pageSize > 0) {
                PageResponse(competitionId, total, pageNumber, sortedComps.drop((pageNumber - 1) * pageSize).take(pageSize).toTypedArray())
            } else {
                PageResponse(competitionId, total, pageNumber, sortedComps.toTypedArray())
            }
        } else {
            if (pageSize != null && pageSize > 0) {
                PageResponse(competitionId, total, 0, sortedComps.take(pageSize).toTypedArray())
            } else {
                PageResponse(competitionId, total, 0, sortedComps.toTypedArray())
            }
        }

    }


    @RequestMapping("/store/categorystate", method = [RequestMethod.GET])
    fun getCategoryState(@RequestParam("categoryId") categoryId: String): CategoryState? = stateQueryService.getCategoryState(Base64.getDecoder().decode(categoryId).toString(StandardCharsets.UTF_8))


    @RequestMapping("/store/competitors", method = [RequestMethod.GET])
    fun getCompetitors(@RequestParam("competitionId") competitionId: String,
                       @RequestParam("searchString") searchString: String?,
                       @RequestParam("pageSize") pageSize: Int?,
                       @RequestParam("pageNumber") pageNumber: Int?): PageResponse<Competitor> {
        val categories = getCategories(competitionId)
        var competitors = categories.filter { it.categoryId != null }.mapNotNull { category ->
            stateQueryService.getCategoryState(category.categoryId!!)
        }.flatMap { categoryState ->
            categoryState.competitors
        }.toTypedArray()

        val total = competitors.size

        if (!searchString.isNullOrBlank() && "undefined" != searchString) {
            competitors = competitors.filter { it.firstName.contains(searchString!!, ignoreCase = true) }.toTypedArray()
            return createPageResponse(competitionId, pageSize, 0, competitors, competitors.size)
        }

        return createPageResponse(competitionId, pageSize, pageNumber, competitors, total)
    }

    @RequestMapping("/store/competitor", method = [RequestMethod.GET])
    fun getCompetitor(@RequestParam("competitionId") competitionId: String,
                      @RequestParam("categoryId") categoryId: String,
                      @RequestParam("fighterId") competitorId: String): Competitor? {
        if (competitionId == "null" || competitionId.isEmpty() || competitorId == "null" || competitorId.isEmpty()) {
            return null
        }
        val decodedFighterId = String(Base64.getDecoder().decode(competitorId))
        return if (categoryId == "null" || categoryId.isEmpty()) {
            val categoryState = getCategories(competitionId)
                    .filter { it.categoryId != null }.mapNotNull { category ->
                        getCategoryState(String(Base64.getEncoder().encode(category.categoryId?.toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))
                    }.firstOrNull { categoryState ->
                        categoryState.competitors.any { it.email == decodedFighterId }
                    }
            categoryState?.competitors?.find { it.email == decodedFighterId }
        } else {
            val categoryState = getCategoryState(String(Base64.getEncoder().encode(categoryId.toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))
            categoryState?.competitors?.find { it.email == decodedFighterId }
        }
    }

    @RequestMapping("/store/comprops", method = [RequestMethod.GET])
    fun getCompetitionProperties(@RequestParam("competitionId") competitionId: String?) = stateQueryService.getCompetitionProperties(competitionId)?.setSchedule(null)


    @RequestMapping("/store/metadata", method = [RequestMethod.GET])
    fun getHostForCategory(@RequestParam("categoryId") categoryId: String): HostStoreInfo? {
        val decodedCatId = String(Base64.getDecoder().decode(categoryId), StandardCharsets.UTF_8)
        log.info("Getting metadata for category $decodedCatId")
        return stateQueryService.doGetHostForCategory(decodedCatId)
    }


    @RequestMapping("/store/competitions", method = [RequestMethod.GET])
    fun getCompetitions(@RequestParam("status") status: CompetitionStatus?, @RequestParam("creatorId") creatorId: String?) = stateQueryService.getCompetitions(status, creatorId)

    @RequestMapping("/store/categories", method = [RequestMethod.GET])
    fun getCategories(@RequestParam("competitionId") competitionId: String) = stateQueryService.getCategories(competitionId)

    @RequestMapping("/store/brackets", method = [RequestMethod.GET])
    fun getBrackets(@RequestParam("competitionId") competitionId: String?, @RequestParam("categoryId") categoryId: String?): Array<BracketDescriptor> {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition id is missing.")
            emptyArray()
        } else {
            if (categoryId.isNullOrBlank()) {
                val categories = getCategories(competitionId!!)
                categories.mapNotNull { cat ->
                    cat.categoryId?.let { getCategoryState(it) }?.brackets
                }.toTypedArray()
            } else {
                //get brackets for category
                getCategoryState(categoryId!!)?.brackets?.let {
                    arrayOf(it)
                } ?: emptyArray()
            }
        }
    }


    @RequestMapping("/store/schedule", method = [RequestMethod.GET])
    fun getSchedule(@RequestParam("competitionId") competitionId: String?): Schedule? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition id is missing.")
            null
        } else {
            stateQueryService.getSchedule(competitionId!!)
        }
    }

    @RequestMapping("/store/defaultcategories", method = [RequestMethod.GET])
    fun getDefaultCategories(@RequestParam("sportsId") sportsId: String?, @RequestParam("competitionId") competitionId: String?): List<Category> {
        return if (sportsId.isNullOrBlank() || competitionId.isNullOrBlank()) {
            log.warn("Sports id is $sportsId, competition ID is $competitionId.")
            emptyList()
        } else {
            categoryGeneratorService.createDefaultBjjCategories(competitionId!!)
        }
    }

    @RequestMapping("/store/dashboardstate", method = [RequestMethod.GET])
    fun getDashboardState(@RequestParam("competitionId") competitionId: String?): CompetitionDashboardState? {
        return if (competitionId.isNullOrBlank()) {
            log.warn("Competition ID is $competitionId.")
            null
        } else {
            stateQueryService.getDashboardState(competitionId!!)
        }
    }

    @RequestMapping("/store/matstate", method = [RequestMethod.GET])
    fun getMatState(@RequestParam("matId") matId: String): MatState? = stateQueryService.getMatState(Base64.getDecoder().decode(matId).toString(StandardCharsets.UTF_8))

    @RequestMapping("/store/matfights", method = [RequestMethod.GET])
    fun queryMatFights(@RequestParam("matId") matId: String, @RequestParam("maxResults") maxResults: Int?, @RequestParam("queryString") queryString: String?): Array<FightDescription> {
        val fights = stateQueryService.getMatState(Base64.getDecoder().decode(matId).toString(StandardCharsets.UTF_8))?.fights?.filter(::fightReady)?.toTypedArray()
                ?: emptyArray()
        val filteredFights = if (queryString.isNullOrBlank() || queryString == "undefined" || queryString == "null") {
            fights
        } else {
            fights.filter { fight ->
                fight.competitors.any {
                    it.competitor.firstName.contains(queryString!!, ignoreCase = true)
                            || it.competitor.lastName.contains(queryString, ignoreCase = true)
                            || it.competitor.academy?.contains(queryString, ignoreCase = true) == true
                }
            }.toTypedArray()
        }
        return filteredFights.take(min(maxResults ?: 10, fights.size)).toTypedArray()
    }

    private fun fightReady(fight: FightDescription) = fight.stage != FightStage.FINISHED && fight.competitors.isNotEmpty() && fight.competitors.all { Schedule.compNotEmpty(it.competitor) }


    @RequestMapping("/store/mats", method = [RequestMethod.GET])
    fun getPeriodMats(@RequestParam("competitionId") competitionId: String?, @RequestParam("periodId") periodId: String?): Array<MatDTO>? {
        return if (!competitionId.isNullOrBlank() && !periodId.isNullOrBlank()) {
            val dashboardState = stateQueryService.getDashboardState(competitionId!!)
            val period = dashboardState?.periods?.find { it.id == periodId }
            if (period != null) {
                val mats = period.matIds.mapNotNull { matId -> stateQueryService.getMatState(matId) }
                mats.map { mat ->
                    val topFiveFights = mat.fights
                            .filter(::fightReady)
                            .sortedBy { it.numberOnMat ?: Int.MAX_VALUE }
                            .take(5)
                    MatDTO(mat.matId, mat.periodId, mat.fights.size, topFiveFights.toTypedArray())
                }.toTypedArray()
            } else {
                emptyArray()
            }
        } else {
            emptyArray()
        }
    }


}