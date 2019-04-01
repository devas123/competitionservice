package compman.compsrv.service

import compman.compsrv.cluster.ClusterSession
import compman.compsrv.jpa.competition.CategoryState
import compman.compsrv.jpa.competition.CompetitionDashboardState
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.repository.*
import io.scalecube.transport.Address
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.*


class StateQueryService(private val clusterSession: ClusterSession,
                        private val restTemplate: RestTemplate,
                        private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                        private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                        private val scheduleCrudRepository: ScheduleCrudRepository,
                        private val categoryCrudRepository: CategoryCrudRepository,
                        private val competitorCrudRepository: CompetitorCrudRepository,
                        private val dashboardStateCrudRepository: DashboardStateCrudRepository,
                        private val bracketsCrudRepository: BracketsCrudRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }


    private fun getLocalCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, pageNumber: Int): Page<Competitor> {
        return if (!searchString.isNullOrBlank()) {
            if (categoryId.isNullOrBlank()) {
                competitorCrudRepository.findByCompetitionIdAndSearchString(competitionId, searchString, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName"))))
            } else {
                competitorCrudRepository.findByCompetitionIdAndCategoryIdAndSearchString(competitionId, categoryId, searchString, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName"))))
            }
        } else {
            if (categoryId.isNullOrBlank()) {
                competitorCrudRepository.findByCompetitionId(competitionId, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName"))))
            } else {
                competitorCrudRepository.findByCompetitionIdAndCategoryId(competitionId, categoryId, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName"))))
            }
        }
    }

    fun getCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, pageNumber: Int): Page<Competitor>? {
        fun getPageType(): ParameterizedTypeReference<Page<Competitor>> {
            return object : ParameterizedTypeReference<Page<Competitor>>() {}
        }
        return getLocalOrRemote(competitionId,
                { getLocalCompetitors(competitionId, categoryId, searchString, pageSize, pageNumber) },
                {
                    val uriBuilder = StringBuilder("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/competitors?competitionId=$competitionId")
                    if (!categoryId.isNullOrBlank()) {
                        uriBuilder.append("&categoryId=$categoryId")
                    }
                    if (!searchString.isNullOrBlank()) {
                        uriBuilder.append("&searchString=$searchString")
                    }
                    uriBuilder.append("&pageSize=$pageSize")
                    uriBuilder.append("&pageNumber=$pageNumber")
                    uriBuilder.append("&internal=true")
                    val typeRef = getPageType()
                    val respEntity = restTemplate.exchange(uriBuilder.toString(), HttpMethod.GET, HttpEntity.EMPTY, typeRef)
                    if (respEntity.statusCode == HttpStatus.OK) {
                        respEntity.body!!
                    } else {
                        throw RestClientResponseException("Error while getting competitors", respEntity.statusCodeValue, respEntity.statusCode.reasonPhrase, respEntity.headers, null, StandardCharsets.UTF_8)
                    }
                })
    }

    fun getCompetitor(competitionId: String, categoryId: String, comptetitorId: String) = competitorCrudRepository.findByUserIdAndCategoryIdAndCompetitionId(comptetitorId, categoryId, competitionId)


    fun getCompetitionState(competitionId: String?): CompetitionState? = getLocalOrRemote(competitionId,
            { competitionId?.let { competitionStateCrudRepository.findById(it).orElse(null) } },
            {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/competitionstate?competitionId=$competitionId&internal=true", CompetitionState::class.java)
            }
    )


    fun getCompetitionProperties(competitionId: String?): CompetitionPropertiesDTO? = getLocalOrRemote(competitionId,
            { competitionId?.let { competitionPropertiesCrudRepository.findById(it).orElse(null) }?.toDTO() },
            {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/comprops?competitionId=$competitionId&internal=true", CompetitionPropertiesDTO::class.java)
            }
    )

    private fun <T> getLocalOrRemote(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address) -> T?): T? {
        return if (competitionId != null) {
            val instanceAddress = clusterSession.findProcessingMember(competitionId)
            if (instanceAddress != null) {
                if (clusterSession.isLocal(instanceAddress)) {
                    ifLocal.invoke()
                } else {
                    ifRemote.invoke(instanceAddress)
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    fun getSchedule(competitionId: String?): compman.compsrv.jpa.schedule.Schedule? {
        return getLocalOrRemote(competitionId,
                {
                    scheduleCrudRepository.findById(competitionId!!).orElse(null)
                },
                {
                    restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/competitionstate?competitionId=$competitionId&internal=true", CompetitionState::class.java)?.schedule
                })
    }

    fun getCategoryState(competitionId: String, categoryId: String): CategoryState? {
        log.info("Getting state for category $categoryId")
        return getLocalOrRemote(competitionId, { categoryCrudRepository.findByCompetitionIdAndCategoryId(competitionId, categoryId) }, {
            val encCompId = Base64.getEncoder().encode(competitionId.toByteArray(StandardCharsets.UTF_8))
            val encCatId = Base64.getEncoder().encode(categoryId.toByteArray(StandardCharsets.UTF_8))
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/categorystate?competitionId=$encCompId&categoryId=$encCatId&internal=true", CategoryState::class.java)
        })
    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        val categories = getLocalOrRemote(competitionId, {
            categoryCrudRepository.findByCompetitionId(competitionId)
        }, {
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/categories?competitionId=$competitionId&internal=true", Array<CategoryState>::class.java)
        }) ?: emptyArray()
        return categories
                .filter { !it.id.isNullOrBlank() }
                .mapNotNull { getCategoryState(competitionId, it.id!!) }
                .map { it.toDTO() }.toTypedArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardState? {
        return dashboardStateCrudRepository.findById(competitionId).orElse(null)
    }

    fun getBracketsForCompetition(competitionId: String) = bracketsCrudRepository.findByCompetitionId(competitionId)

    fun getBrackets(categoryId: String) = bracketsCrudRepository.findById(categoryId)
}