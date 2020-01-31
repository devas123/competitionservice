package compman.compsrv.service

import arrow.core.Either
import arrow.fx.IO
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.PageResponse
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatStateDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.*
import compman.compsrv.util.compNotEmpty
import io.scalecube.net.Address
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
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
import java.time.Duration
import java.util.stream.Collectors
import javax.transaction.Transactional
import kotlin.math.max
import kotlin.streams.toList

@Component
class StateQueryService(private val clusterSession: ClusterSession,
                        restTemplateBuilder: RestTemplateBuilder,
                        private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                        private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                        private val scheduleCrudRepository: ScheduleCrudRepository,
                        private val fightCrudRepository: FightCrudRepository,
                        private val categoryStateCrudRepository: CategoryStateCrudRepository,
                        private val matDescriptionCrudRepository: MatDescriptionCrudRepository,
                        private val categoryDescriptorCrudRepository: CategoryDescriptorCrudRepository,
                        private val competitorCrudRepository: CompetitorCrudRepository,
                        private val dashboardStateCrudRepository: DashboardStateCrudRepository,
                        private val dashboardPeriodCrudRepository: DashboardPeriodCrudRepository) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }

    private val firstName = "firstName"

    private val restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(10)).build()

    private val lastName = "lastName"


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

    fun getCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, pageNumber: Int): PageResponse<CompetitorDTO>? {
        fun getPageType(): ParameterizedTypeReference<PageResponse<CompetitorDTO>> {
            return object : ParameterizedTypeReference<PageResponse<CompetitorDTO>>() {}
        }
        return localOrRemote(competitionId,
                {
                    val page = getLocalCompetitors(competitionId, categoryId, searchString, pageSize, pageNumber)
                    PageResponse(competitionId, page.totalElements, page.number + 1, page.content.toTypedArray())

                },
                { _, restTemplate, urlPrefix ->
                    val uri = "$urlPrefix/api/v1/store/competitors?competitionId=$competitionId"
                    val queryParams = StringBuilder()
                    if (!categoryId.isNullOrBlank()) {
                        queryParams.append("&categoryId=").append(categoryId)
                    }
                    if (!searchString.isNullOrBlank()) {
                        queryParams.append("&searchString=").append(searchString)
                    }
                    queryParams.append("&pageSize=").append(pageSize.toString())
                    queryParams.append("&pageNumber=").append(pageNumber.toString())

                    val typeRef = getPageType()
                    val respEntity = restTemplate.exchange(uri + queryParams.toString(), HttpMethod.GET, HttpEntity.EMPTY, typeRef)
                    if (respEntity.statusCode == HttpStatus.OK) {
                        respEntity.body
                    } else {
                        throw RestClientResponseException("Error while getting competitors", respEntity.statusCodeValue, respEntity.statusCode.reasonPhrase, respEntity.headers, null, StandardCharsets.UTF_8)
                    }
                })
    }

    fun getCompetitor(competitionId: String, fighterId: String) = localOrRemote(competitionId, { competitorCrudRepository.findById(fighterId).map { it.toDTO() }.orElse(null) },
            { _, restTemplate, urlPrefix ->
                restTemplate.getForObject("$urlPrefix/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId", CompetitorDTO::class.java)
            })


    fun getCompetitionState(competitionId: String?): CompetitionStateDTO? {
        return localOrRemote(competitionId,
                { competitionStateCrudRepository.findByIdOrNull(competitionId)?.toDTO() },
                { _, restTemplate, url ->
                    restTemplate.getForObject("$url/api/v1/store/competitionstate?competitionId=$competitionId", CompetitionStateDTO::class.java)
                })
    }


    fun getCompetitionInfoTemplate(competitionId: String?): ByteArray? {
        return localOrRemote(competitionId,
                { competitionStateCrudRepository.findByIdOrNull(competitionId)?.competitionInfoTemplate },
                { _, restTemplate, url ->
                    restTemplate.getForObject("$url/api/v1/store/infotemplate?competitionId=$competitionId", ByteArray::class.java)
                }
        )
    }

    fun getCompetitionProperties(competitionId: String?): CompetitionPropertiesDTO? {
        return localOrRemote(competitionId,
                {
                    log.info("Getting competition properties id $competitionId")
                    competitionPropertiesCrudRepository.findByIdOrNull(competitionId)?.toDTO(competitionStateCrudRepository.findStatusById(competitionId!!)?.getStatus())
                },
                { address, restTemplate, urlPrefix ->
                    val url = "$urlPrefix/api/v1/store/comprops?competitionId=$competitionId"
                    log.info("Doing a remote request to address $address, url=$url")
                    val result = restTemplate.getForObject(url, CompetitionPropertiesDTO::class.java)
                    log.info("Result: $result")
                    result
                }
        )
    }

    fun <T> localOrRemote(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> T?): T? {
        var k: Either<Throwable, T?> = Either.left(RuntimeException("Request failed for competition $competitionId, see logs for details. "))
        competitionId?.let { id ->
            var retries = 3
            do {
                log.info("Looking for processing member for competition.")
                val instanceAddress = clusterSession.findProcessingMember(competitionId)
                log.info("Competition $id is processed by $instanceAddress")
                instanceAddress?.let {
                    k = IO {
                        if (clusterSession.isLocal(instanceAddress)) {
                            log.info("Competition $competitionId is processed locally!")
                            ifLocal.invoke()
                        } else {
                            log.info("Competition $competitionId is processed by $instanceAddress")
                            ifRemote.invoke(instanceAddress, restTemplate, clusterSession.getUrlPrefix(it.host(), it.port()))
                        }
                    }.attempt().unsafeRunSync().mapLeft {
                        log.error("Error while doing a remote request.", it)
                        clusterSession.invalidateMemberForCompetitionId(id)
                        retries--
                        it
                    }
                }
            } while (retries > 0 && k.isLeft())
        }
        return k.fold({ throw it }, {
            it
        })
    }

    fun getSchedule(competitionId: String?): ScheduleDTO? {
        return localOrRemote(competitionId,
                {
                    scheduleCrudRepository.findById(competitionId!!).orElse(null)?.toDTO()
                },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/schedule?competitionId=$competitionId", ScheduleDTO::class.java)
                })
    }

    private val getMat = { id: String? -> id?.let { matDescriptionCrudRepository.findByIdOrNull(id)?.toDTO() } }
    private val getCategory = { id: String -> categoryDescriptorCrudRepository.findByIdOrNull(id)?.toDTO() }


    fun getCategoryState(competitionId: String, categoryId: String): CategoryStateDTO? {
        log.info("Getting state for category $categoryId")
        return localOrRemote(competitionId, {
            categoryStateCrudRepository.findByIdAndCompetitionId(categoryId, competitionId)?.toDTO(includeBrackets = true, competitionId = competitionId)
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId", CategoryStateDTO::class.java)
                })
    }


    fun getMats(competitionId: String, periodId: String): List<MatStateDTO>? {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return localOrRemote(competitionId, {
            dashboardPeriodCrudRepository.findByIdOrNull(periodId)?.mats?.map { mat ->
                val pageRequest = PageRequest.of(0, 5, Sort.unsorted())
                val matDescrDto = mat.toDTO()
                val topFiveFights =
                        fightCrudRepository.findDistinctByCompetitionIdAndMatIdAndStatusInAndScoresNotNullOrderByNumberOnMat(competitionId, mat.id!!,
                                FightCrudRepository.notFinishedStatuses, pageRequest)?.content
                                ?.filter { f -> f.scores?.size == 2 && (f.scores?.all { compNotEmpty(it.competitor) } == true) }
                                ?.map { it.toDTO(getCategory, { matDescrDto }) }
                                ?.toTypedArray()
                MatStateDTO()
                        .setMatDescription(matDescrDto)
                        .setNumberOfFights(fightCrudRepository.countByMatId(mat.id!!))
                        .setTopFiveFights(topFiveFights)
            }
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/mats?competitionId=$competitionId&periodId=$periodId", Array<MatStateDTO>::class.java)?.toList()
                            ?: emptyList()
                })
    }

    fun getMatFights(competitionId: String, matId: String, maxResults: Int): List<FightDescriptionDTO>? {
        log.info("Getting competitors for competition $competitionId and mat $matId")
        return localOrRemote(competitionId, {
            val pageRequest = PageRequest.of(0, 10, Sort.unsorted())
            fightCrudRepository.findDistinctByCompetitionIdAndMatIdAndStatusInAndScoresNotNullOrderByNumberOnMat(competitionId, matId, FightCrudRepository.notFinishedStatuses, pageRequest)?.get()
                    ?.filter { f -> f.scores?.size == 2 && (f.scores?.all { compNotEmpty(it.competitor) } == true) }
                    ?.map { it.toDTO(getCategory, getMat) }
                    ?.collect(Collectors.toList())
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults", Array<FightDescriptionDTO>::class.java)?.toList()
                    ?: emptyList()
        })
    }

    @Transactional
    fun getStageFights(competitionId: String, stageId: String): Array<FightDescriptionDTO>? {
        log.info("Getting competitors for stage $stageId")
        return localOrRemote(competitionId, {
            val unmappedFights = fightCrudRepository.findAllByStageId(stageId).toList()
            val categories = categoryDescriptorCrudRepository.findAllById(unmappedFights.map { it.categoryId }.distinct())
            val mats = matDescriptionCrudRepository.findAllById(unmappedFights.mapNotNull { it.matId }.distinct())
            unmappedFights.map { fightDescription ->
                fightDescription.toDTO({ categories.firstOrNull { cat -> cat.id == it }?.toDTO() }, { id -> id?.let { mats.firstOrNull { mat -> mat.id == it }?.toDTO() } })
            }.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$stageId", Array<FightDescriptionDTO>::class.java)
        })

    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        return localOrRemote(competitionId, {
            categoryStateCrudRepository.findByCompetitionId(competitionId)?.filter { !it.id.isNullOrBlank() }?.map { it.toDTO(competitionId = competitionId) }?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/categories?competitionId=$competitionId", Array<CategoryStateDTO>::class.java)
        }) ?: emptyArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardStateDTO? {
        return localOrRemote(competitionId, {
            dashboardStateCrudRepository.findByIdOrNull(competitionId)?.toDTO()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/dashboardstate?competitionId=$competitionId", CompetitionDashboardStateDTO::class.java)
        })
    }
}