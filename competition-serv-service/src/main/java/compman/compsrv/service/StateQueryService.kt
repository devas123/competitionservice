package compman.compsrv.service

import arrow.core.None
import arrow.core.Option
import arrow.core.getOrHandle
import arrow.fx.IO
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.dto.brackets.BracketDescriptorDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatStateDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.*
import compman.compsrv.util.compNotEmpty
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
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import kotlin.math.max

@Component
class StateQueryService(private val clusterSession: ClusterSession,
                        private val restTemplate: RestTemplate,
                        private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                        private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                        private val scheduleCrudRepository: ScheduleCrudRepository,
                        private val fightCrudRepository: FightCrudRepository,
                        private val categoryStateCrudRepository: CategoryStateCrudRepository,
                        private val categoryDescriptorCrudRepository: CategoryDescriptorCrudRepository,
                        private val competitorCrudRepository: CompetitorCrudRepository,
                        private val dashboardStateCrudRepository: DashboardStateCrudRepository,
                        private val dashboardPeriodCrudRepository: DashboardPeriodCrudRepository,
                        private val bracketsCrudRepository: BracketsCrudRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }

    private val firstName = "firstName"

    private val lastName = "lastName"
    private val notFinished = listOf(FightStage.PENDING, FightStage.IN_PROGRESS, FightStage.GET_READY, FightStage.PAUSED)



    private fun getLocalCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, page: Int): Page<CompetitorDTO> {
        val pageNumber = max(page - 1, 0)
        return if (!searchString.isNullOrBlank()) {
            if (categoryId.isNullOrBlank()) {
                competitorCrudRepository.findByCompetitionIdAndSearchString(competitionId, searchString, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc(firstName), Sort.Order.asc(lastName)))).map { it.toDTO() }
            } else {
                competitorCrudRepository.findByCompetitionIdAndCategoryIdAndSearchString(competitionId, categoryId, searchString, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc(firstName), Sort.Order.asc(lastName)))).map { it.toDTO() }
            }
        } else {
            if (categoryId.isNullOrBlank()) {
                val competitors = competitorCrudRepository.findByCompetitionId(competitionId, PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc(firstName), Sort.Order.asc(lastName))))
                competitors.map { it.toDTO() }
            } else {
                competitorCrudRepository.findByCompetitionIdAndCategoriesContaining(competitionId, setOf(categoryId), PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.asc(firstName), Sort.Order.asc(lastName)))).map { it.toDTO() }
            }
        }
    }

    fun getCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, pageNumber: Int): Page<CompetitorDTO>? {
        fun getPageType(): ParameterizedTypeReference<Page<Competitor>> {
            return object : ParameterizedTypeReference<Page<Competitor>>() {}
        }
        return localOrRemote(competitionId,
                { getLocalCompetitors(competitionId, categoryId, searchString, pageSize, pageNumber) },
                { address, restTemplate, _ ->
                    val uri = "${clusterSession.getUrlPrefix(address.host(), address.port())}/api/v1/store/competitors?competitionId=$competitionId"
                    val queryParams = mutableMapOf<String, String>()
                    if (!categoryId.isNullOrBlank()) {
                        queryParams["categoryId"] = categoryId
                    }
                    if (!searchString.isNullOrBlank()) {
                        queryParams["searchString"] = searchString
                    }
                    queryParams["pageSize"] = pageSize.toString()
                    queryParams["pageNumber"] = pageNumber.toString()

                    val typeRef = getPageType()
                    val respEntity = restTemplate.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, typeRef, queryParams)
                    if (respEntity.statusCode == HttpStatus.OK) {
                        respEntity.body?.map { competitor -> competitor.toDTO() }
                    } else {
                        throw RestClientResponseException("Error while getting competitors", respEntity.statusCodeValue, respEntity.statusCode.reasonPhrase, respEntity.headers, null, StandardCharsets.UTF_8)
                    }
                })
    }

    fun getCompetitor(competitionId: String, fighterId: String) = localOrRemote(competitionId, { competitorCrudRepository.findById(fighterId).map { it.toDTO() }.orElse(null) },
            { address, restTemplate, _ ->
                restTemplate.getForObject("${clusterSession.getUrlPrefix(address.host(), address.port())}/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId", CompetitorDTO::class.java)
            })


    fun getCompetitionState(competitionId: String?): CompetitionStateDTO? {
        val io = localOrRemote(competitionId,
                { IO { Option.fromNullable(competitionStateCrudRepository.findByIdOrNull(competitionId)).map { state -> state.toDTO() } } },
                { it, restTemplate, _ ->
                    IO { Option.fromNullable(restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/competitionstate?competitionId=$competitionId", CompetitionStateDTO::class.java)) }
                }
        )
        return io?.attempt()?.unsafeRunSync()?.getOrHandle {
            log.error("Error when getting competition state.", it)
            None
        }?.orNull()
    }

    fun getCompetitionProperties(competitionId: String?): CompetitionPropertiesDTO? {
        val io = localOrRemote(competitionId,
                {
                    log.info("Getting competition properties id $competitionId")
                    IO { Option.fromNullable(competitionPropertiesCrudRepository.findByIdOrNull(competitionId)).map { state -> state.toDTO() } }
                },
                { it, restTemplate, _ ->
                    IO { Option.fromNullable(restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/comprops", CompetitionPropertiesDTO::class.java, mutableMapOf("competitionId" to competitionId))) }
                }
        )
        return io?.attempt()?.unsafeRunSync()?.getOrHandle {
            log.error("Error when getting competition state.", it)
            None
        }?.orNull()
    }

    fun <T> localOrRemote(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> T?): T? {
        return competitionId?.let { id ->
            log.info("Looking for processing member for competition.")
            val instanceAddress = clusterSession.findProcessingMember(competitionId)
            log.info("Competition $id is processed by $instanceAddress")
            instanceAddress?.let {
                if (clusterSession.isLocal(instanceAddress)) {
                    log.info("Competition $competitionId is processed locally!")
                    ifLocal.invoke()
                } else {
                    log.info("Competition $competitionId is processed by $instanceAddress")
                    ifRemote.invoke(instanceAddress, restTemplate, clusterSession.getUrlPrefix(it.host(), it.port()))
                }
            }
        }
    }

    fun getSchedule(competitionId: String?): ScheduleDTO? {
        return localOrRemote(competitionId,
                {
                    scheduleCrudRepository.findById(competitionId!!).orElse(null)?.toDTO()
                },
                { it, restTemplate, _ ->
                    restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/schedule?competitionId=$competitionId", ScheduleDTO::class.java)
                })
    }

    fun getCategoryState(competitionId: String, categoryId: String): CategoryStateDTO? {
        log.info("Getting state for category $categoryId")
        return localOrRemote(competitionId, { categoryStateCrudRepository.findByIdAndCompetitionId(categoryId, competitionId)?.toDTO(includeBrackets = true, competitionId = competitionId) },
                { it, restTemplate, _ ->
                    restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId", CategoryStateDTO::class.java)
                })
    }


    fun getMats(competitionId: String, periodId: String): List<MatStateDTO>? {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return localOrRemote(competitionId, {
            dashboardPeriodCrudRepository.findByIdOrNull(periodId)?.mats?.map { mat ->
                val pageRequest = PageRequest.of(0, 5, Sort.unsorted())
                val topFiveFights =
                        fightCrudRepository.findDistinctByCompetitionIdAndMatIdAndStageInAndScoresNotNullOrderByNumberOnMat(competitionId, mat.id!!,
                        notFinished, pageRequest)?.content?.map { it.toDTO{id -> categoryDescriptorCrudRepository.findByIdOrNull(id)?.toDTO()} }?.toTypedArray()
                MatStateDTO()
                        .setMatDescription(mat.toDTO())
                        .setNumberOfFights(fightCrudRepository.countByMatId(mat.id!!))
                        .setTopFiveFights(topFiveFights)
            }
        },
                { it, restTemplate, _ ->
                    restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/mats?competitionId=$competitionId&periodId=$periodId", Array<MatStateDTO>::class.java)?.toList()
                            ?: emptyList()
                })
    }

    fun getMatFights(competitionId: String, matId: String, maxResults: Int): List<FightDescriptionDTO>? {
        log.info("Getting competitors for competition $competitionId and mat $matId")
        return localOrRemote(competitionId, {
            val pageRequest = PageRequest.of(0, 10, Sort.unsorted())
            fightCrudRepository.findDistinctByCompetitionIdAndMatIdAndStageInAndScoresNotNullOrderByNumberOnMat(competitionId, matId, notFinished, pageRequest)?.get()
                    ?.filter { f -> f.scores?.size == 2 && (f.scores?.all { compNotEmpty(it.competitor) } == true) }
                    ?.map { it.toDTO{id -> categoryDescriptorCrudRepository.findByIdOrNull(id)?.toDTO()} }
                    ?.collect(Collectors.toList())
        }, { it, restTemplate, _ ->
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults", Array<FightDescriptionDTO>::class.java)?.toList()
                    ?: emptyList()
        })
    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        return localOrRemote(competitionId, {
            categoryStateCrudRepository.findByCompetitionId(competitionId)?.filter { !it.id.isNullOrBlank() }?.map { it.toDTO(competitionId = competitionId) }?.toTypedArray()
        }, { it, restTemplate, _ ->
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/categories?competitionId=$competitionId", Array<CategoryStateDTO>::class.java)
        }) ?: emptyArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardStateDTO? {
        return localOrRemote(competitionId, {
            dashboardStateCrudRepository.findByIdOrNull(competitionId)?.toDTO()
        }, { it, restTemplate, _ ->
            restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/dashboardstate?competitionId=$competitionId", CompetitionDashboardStateDTO::class.java)
        })
    }

    fun getBracketsForCompetition(competitionId: String): Array<BracketDescriptorDTO>? =
            localOrRemote(competitionId, {
                bracketsCrudRepository.findByCompetitionId(competitionId)?.mapNotNull { it.toDTO { id -> categoryDescriptorCrudRepository.findByIdOrNull(id)?.toDTO() } }?.toTypedArray()
            }, { it, restTemplate, _ ->
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/brackets?competitionId=$competitionId", Array<BracketDescriptorDTO>::class.java)
            })

    fun getBrackets(competitionId: String, categoryId: String): Array<BracketDescriptorDTO>? =
            localOrRemote(competitionId, {
                val category = categoryDescriptorCrudRepository.findByIdOrNull(categoryId)?.toDTO()
                bracketsCrudRepository.findByIdOrNull(categoryId)?.toDTO{category}?.let {arrayOf(it)} ?: emptyArray()
            }, { it, restTemplate, _ ->
                restTemplate.getForObject("${clusterSession.getUrlPrefix(it.host(), it.port())}/api/v1/store/brackets?competitionId=$competitionId&categoryId=$categoryId", Array<BracketDescriptorDTO>::class.java)
            })
}