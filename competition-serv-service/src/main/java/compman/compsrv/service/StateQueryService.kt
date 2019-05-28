package compman.compsrv.service

import compman.compsrv.cluster.ClusterSession
import compman.compsrv.jpa.competition.CompetitionDashboardState
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.model.dto.brackets.BracketDescriptorDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.*
import io.scalecube.transport.Address
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors


class StateQueryService(private val clusterSession: ClusterSession,
                        private val restTemplate: RestTemplate,
                        private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                        private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                        private val scheduleCrudRepository: ScheduleCrudRepository,
                        private val fightCrudRepository: FightCrudRepository,
                        private val categoryStateCrudRepository: CategoryStateCrudRepository,
                        private val competitorCrudRepository: CompetitorCrudRepository,
                        private val dashboardStateCrudRepository: DashboardStateCrudRepository,
                        private val dashboardPeriodCrudRepository: DashboardPeriodCrudRepository,
                        private val bracketsCrudRepository: BracketsCrudRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }


    private fun getLocalCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, page: Int): Page<CompetitorDTO> {
        val pageNumber = if (page > 0) {
            page - 1
        } else {
            0
        }
        return if (!searchString.isNullOrBlank()) {
            if (categoryId.isNullOrBlank()) {
                competitorCrudRepository.findByCompetitionIdAndSearchString(competitionId, searchString, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName")))).map { it.toDTO() }
            } else {
                competitorCrudRepository.findByCompetitionIdAndCategoryIdAndSearchString(competitionId, categoryId, searchString, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName")))).map { it.toDTO() }
            }
        } else {
            if (categoryId.isNullOrBlank()) {
                val competitors = competitorCrudRepository.findByCompetitionId(competitionId, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName"))))
                competitors.map { it.toDTO() }
            } else {
                competitorCrudRepository.findByCompetitionIdAndCategoryId(competitionId, categoryId, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName")))).map { it.toDTO() }
            }
        }
    }

    fun getCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, pageNumber: Int): Page<CompetitorDTO>? {
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
                    val typeRef = getPageType()
                    val respEntity = restTemplate.exchange(uriBuilder.toString(), HttpMethod.GET, HttpEntity.EMPTY, typeRef)
                    if (respEntity.statusCode == HttpStatus.OK) {
                        respEntity.body?.map { competitor -> competitor.toDTO() }
                    } else {
                        throw RestClientResponseException("Error while getting competitors", respEntity.statusCodeValue, respEntity.statusCode.reasonPhrase, respEntity.headers, null, StandardCharsets.UTF_8)
                    }
                })
    }

    fun getCompetitor(competitionId: String, fighterId: String) = getLocalOrRemote(competitionId, { competitorCrudRepository.findById(fighterId).map { it.toDTO() }.orElse(null) },
            {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId", CompetitorDTO::class.java)
            })


    fun getCompetitionState(competitionId: String?): CompetitionStateDTO? = getLocalOrRemote(competitionId,
            { competitionId?.let { competitionStateCrudRepository.findById(it).orElse(null)?.toDTO() } },
            {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/competitionstate?competitionId=$competitionId", CompetitionStateDTO::class.java)
            }
    )


    fun getCompetitionProperties(competitionId: String?): CompetitionPropertiesDTO? = getLocalOrRemote(competitionId,
            { competitionId?.let { competitionPropertiesCrudRepository.findById(it).orElse(null) }?.toDTO() },
            {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/comprops?competitionId=$competitionId", CompetitionPropertiesDTO::class.java)
            }
    )

    private fun <T> getLocalOrRemote(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address) -> T?): T? {
        return if (competitionId != null) {
            val instanceAddress = clusterSession.findProcessingMember(competitionId)
            if (instanceAddress != null) {
                log.info("Competition $competitionId is processed locally!")
                if (clusterSession.isLocal(instanceAddress)) {
                    ifLocal.invoke()
                } else {
                    log.info("Competition $competitionId is processed by $instanceAddress")
                    ifRemote.invoke(instanceAddress)
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    fun getSchedule(competitionId: String?): ScheduleDTO? {
        return getLocalOrRemote(competitionId,
                {
                    scheduleCrudRepository.findById(competitionId!!).orElse(null)?.toDTO()
                },
                {
                    restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/schedule?competitionId=$competitionId", ScheduleDTO::class.java)
                })
    }

    fun getCategoryState(competitionId: String, categoryId: String): CategoryStateDTO? {
        log.info("Getting state for category $categoryId")
        return getLocalOrRemote(competitionId, { categoryStateCrudRepository.findByIdAndCompetitionId(categoryId, competitionId)?.toDTO(includeBrackets = true) }, {
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId", CategoryStateDTO::class.java)
        })
    }

    fun getMats(competitionId: String, periodId: String): List<MatDTO>? {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return getLocalOrRemote(competitionId, {
            dashboardPeriodCrudRepository.findByIdOrNull(periodId)?.matIds?.map { matId ->
                val pageRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "startTime"))
                val topFiveFights = fightCrudRepository.findByCompetitionIdAndMatId(competitionId, matId, pageRequest)?.content?.map { it.toDTO() }?.toTypedArray()
                MatDTO()
                        .setMatId(matId)
                        .setNumberOfFights(fightCrudRepository.countByMatId(matId))
                        .setTopFiveFights(topFiveFights)
            }
        }, {
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/mats?competitionId=$competitionId&periodId=$periodId", Array<MatDTO>::class.java)?.toList()
                    ?: emptyList()
        })
    }

    fun getMatFights(competitionId: String, matId: String, maxResults: Int): List<FightDescriptionDTO>? {
        log.info("Getting competitors for competition $competitionId and mat $matId")
        return getLocalOrRemote(competitionId, {
            fightCrudRepository.findByCompetitionIdAndMatId(competitionId, matId, PageRequest.of(0, maxResults, Sort.by(Sort.Direction.ASC, "startTime")))?.get()
                    ?.filter { f -> f.scores.size == 2 && f.scores.all { Schedule.compNotEmpty(it.competitor) } }
                    ?.map { it.toDTO() }?.collect(Collectors.toList())
        }, {
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults", Array<FightDescriptionDTO>::class.java)?.toList()
                    ?: emptyList()
        })
    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        return getLocalOrRemote(competitionId, {
            categoryStateCrudRepository.findByCompetitionId(competitionId)?.filter { !it.id.isNullOrBlank() }?.map { it.toDTO() }?.toTypedArray()
        }, {
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/categories?competitionId=$competitionId", Array<CategoryStateDTO>::class.java)
        }) ?: emptyArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardStateDTO? {
        return getLocalOrRemote(competitionId, {
            dashboardStateCrudRepository.findById(competitionId).orElse(null)?.toDTO()
        }, {
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/dashboardstate?competitionId=$competitionId", CompetitionDashboardStateDTO::class.java)
        })
    }

    fun getBracketsForCompetition(competitionId: String): Array<BracketDescriptorDTO>? =
            getLocalOrRemote(competitionId, {
                bracketsCrudRepository.findByCompetitionId(competitionId)?.mapNotNull { it.toDTO() }?.toTypedArray()
            }, {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/brackets?competitionId=$competitionId", Array<BracketDescriptorDTO>::class.java)
            })

    fun getBrackets(competitionId: String, categoryId: String): Array<BracketDescriptorDTO>? =
            getLocalOrRemote(competitionId, {
                bracketsCrudRepository.findById(categoryId).map { it.toDTO()!! }.map { arrayOf(it) }.orElse(emptyArray())
            }, {
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/brackets?competitionId=$competitionId&categoryId=$categoryId", Array<BracketDescriptorDTO>::class.java)
            })
}