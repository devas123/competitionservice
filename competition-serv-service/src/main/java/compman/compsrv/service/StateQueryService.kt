package compman.compsrv.service

import arrow.core.Option
import arrow.core.getOrElse
import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.daos.*
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.PageResponse
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.JooqQueryProvider
import compman.compsrv.repository.JooqRepository
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.copy
import compman.compsrv.util.toMonoOrEmpty
import io.scalecube.net.Address
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import kotlin.math.max

@Component
class StateQueryService(private val clusterSession: ClusterSession,
                        restTemplateBuilder: RestTemplateBuilder,
                        private val jooq: JooqRepository,
                        private val jooqQueryProvider: JooqQueryProvider,
                        private val registrationInfoDao: RegistrationInfoDao,
                        private val registrationPeriodDao: RegistrationPeriodDao,
                        private val registrationGroupDao: RegistrationGroupDao,
                        private val regGroupRegPeriodDao: RegGroupRegPeriodDao,
                        private val staffDao: CompetitionPropertiesStaffIdsDao,
                        private val promoCodeDao: PromoCodeDao,
                        private val competitionPropertiesDao: CompetitionPropertiesDao,
                        private val fightResultOptionDao: FightResultOptionDao,
                        private val fightDescriptionDao: FightDescriptionDao,
                        private val competitorCrudRepository: CompetitorDao) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }

    private val restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(10)).build()

    private fun getLocalCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, page: Int): PageResponse<CompetitorDTO> {
        val pageNumber = max(page - 1, 0)
        val fromJoinedTables = jooqQueryProvider.competitorsQuery(competitionId)
        val count = jooq.competitorsCount(competitionId, categoryId)

        val prefetch = if (!searchString.isNullOrBlank()) {
            if (categoryId.isNullOrBlank()) {
                fromJoinedTables
                        .and(Competitor.COMPETITOR.FIRST_NAME.like(searchString).or(Competitor.COMPETITOR.LAST_NAME.like(searchString)))
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
            } else {
                fromJoinedTables
                        .and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(categoryId))
                        .and(Competitor.COMPETITOR.FIRST_NAME.like(searchString)
                                .or(Competitor.COMPETITOR.LAST_NAME.like(searchString)))
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
            }
        } else {
            if (categoryId.isNullOrBlank()) {
                fromJoinedTables
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
            } else {
                fromJoinedTables
                        .and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(categoryId))
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
            }
        }

        val limited = if (pageSize > 0) {
            prefetch.limit(pageSize)
                    .offset(pageNumber * pageSize)
        } else {
            prefetch
        }

        val competitors = limited.fetch { rec ->
            jooq.mapToCompetitor(rec, competitionId)
        }
                .groupBy { it.id }
                .map { e ->
                    e.value.reduce { acc, competitorDTO ->
                        acc?.copy(categories = (acc.categories + competitorDTO.categories))
                                ?: competitorDTO
                    }
                }
        return PageResponse(competitionId, count.toLong(), page, competitors.toTypedArray())
    }

    fun getCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, pageNumber: Int): PageResponse<CompetitorDTO>? {
        fun getPageType(): ParameterizedTypeReference<PageResponse<CompetitorDTO>> {
            return object : ParameterizedTypeReference<PageResponse<CompetitorDTO>>() {}
        }
        return localOrRemote(competitionId,
                {
                    getLocalCompetitors(competitionId, categoryId, searchString, pageSize, pageNumber).toMonoOrEmpty()
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
                    val body = if (respEntity.statusCode == HttpStatus.OK) {
                        respEntity.body
                    } else {
                        throw RestClientResponseException("Error while getting competitors", respEntity.statusCodeValue, respEntity.statusCode.reasonPhrase, respEntity.headers, null, StandardCharsets.UTF_8)
                    }
                    body.toMonoOrEmpty()
                })
    }

    fun getCompetitor(competitionId: String, fighterId: String) = localOrRemote(competitionId, {
        competitorCrudRepository.findById(fighterId)?.toDTO(jooq.getCompetitorCategories(competitionId, fighterId).toTypedArray()).toMonoOrEmpty()
    },
            { _, restTemplate, urlPrefix ->
                restTemplate.getForObject("$urlPrefix/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId", CompetitorDTO::class.java).toMonoOrEmpty()
            })

    fun getFightResultOptions(competitionId: String, fightId: String) = localOrRemote(competitionId, {
        val fight = fightDescriptionDao.findById(fightId)
        fight?.let { fightResultOptionDao.fetchByStageId(it.stageId)?.map { fr -> fr.toDTO() } }?.toTypedArray().toMonoOrEmpty()
    },
            { _, restTemplate, urlPrefix ->
                restTemplate.getForObject("$urlPrefix/api/v1/store/fightresultoptions?competitionId=$competitionId&fightId=$fightId", Array<FightResultOptionDTO>::class.java).toMonoOrEmpty()
            })


    fun getCompetitionInfoTemplate(competitionId: String?): ByteArray? {
        return localOrRemote(competitionId,
                { competitionPropertiesDao.findById(competitionId)?.competitionInfoTemplate.toMonoOrEmpty() },
                { _, restTemplate, url ->
                    restTemplate.getForObject("$url/api/v1/store/infotemplate?competitionId=$competitionId", ByteArray::class.java).toMonoOrEmpty()
                }
        )
    }

    fun getRegistrationInfo(competitionId: String): Mono<RegistrationInfoDTO> {
        return localOrRemoteIo(competitionId, {
            Mono.just(registrationInfoDao.findById(competitionId))
                    .flatMap { regInfo ->
                        val periods = registrationPeriodDao.fetchByRegistrationInfoId(competitionId)
                        val groups = registrationGroupDao.fetchByRegistrationInfoId(competitionId)
                        val connections = regGroupRegPeriodDao.fetchByRegPeriodId(*periods.mapNotNull { it.id }.toTypedArray())
                        val categoryIds = jooq.fetchCategoryIdsByRegistrationGroupIds(groups.mapNotNull { it.id })
                        categoryIds.collectList()
                                .map { groupIdsToCategoryIds ->
                                    regInfo.toDTO(
                                            registrationPeriods = periods.map { period -> period.toDTO { perId -> connections.filter { con -> con.regPeriodId == perId }.map { id -> id.regGroupId }.toTypedArray() }}.toTypedArray(),
                                            registrationGroups = groups.map { group ->
                                                group.toDTO({ grId ->
                                                groupIdsToCategoryIds.filter { it.a == grId }.flatMap { it.b }.toTypedArray()
                                            }, { groupId ->
                                                connections.filter { con -> con.regGroupId == groupId }.map { it.regPeriodId }.toTypedArray()
                                            }) }.toTypedArray()
                                    )
                                }
                    }
        },
                { address, restTemplate, urlPrefix ->
                    val url = "$urlPrefix/api/v1/store/reginfo?competitionId=$competitionId"
                    log.info("Doing a remote request to address $address, url=$url")
                    val result = restTemplate.getForObject(url, RegistrationInfoDTO::class.java)
                    log.info("Result: $result")
                    result.toMonoOrEmpty()
                })
    }

    fun getCompetitionProperties(competitionId: String): Mono<Option<CompetitionPropertiesDTO>> {
        return localOrRemoteIo(competitionId,
                {
                    log.info("Getting competition properties id $competitionId")
                    val result = competitionPropertiesDao.findById(competitionId)
                            ?.toDTO(staffDao.fetchByCompetitionPropertiesId(competitionId)?.map { it.staffId }?.toTypedArray(),
                                    promoCodeDao.fetchByCompetitionId(competitionId)?.map { it.toDTO() }?.toTypedArray())
                    log.info("Found competition properties: $result")
                    result.toMonoOrEmpty()
                },
                { address, restTemplate, urlPrefix ->
                    val url = "$urlPrefix/api/v1/store/comprops?competitionId=$competitionId"
                    log.info("Doing a remote request to address $address, url=$url")
                    val result = restTemplate.getForObject(url, CompetitionPropertiesDTO::class.java)
                    log.info("Result: $result")
                    result.toMonoOrEmpty()
                }
        ).map { io -> Option.fromNullable(io) }
    }

    fun <T> localOrRemoteIo(competitionId: String?, ifLocal: () -> Mono<T>, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> Mono<T>): Mono<T> =
            Option.fromNullable(competitionId).map { id ->
                val instanceAddress = clusterSession.findProcessingMember(id)
                instanceAddress.filter { it != null }.flatMap { address ->
                    clusterSession.invalidateMemberForCompetitionId(id)
                    if (clusterSession.isLocal(address!!)) {
                        log.info("Competition $competitionId is processed locally. Starting executing the logic.")
                        ifLocal()
                    } else {
                        log.debug("Competition $competitionId is processed by $address")
                        ifRemote(address, restTemplate, clusterSession.getUrlPrefix(address.host(), address.port()))
                    }
                }.retryBackoff(3, Duration.ofMillis(10))
            }.getOrElse { Mono.empty() }

    fun <T> localOrRemote(competitionId: String?, ifLocal: () -> Mono<T>, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> Mono<T>): T? {
        return localOrRemoteIo(competitionId, ifLocal, ifRemote).block(Duration.ofMillis(30000))
    }

    fun getSchedule(competitionId: String): ScheduleDTO? {
        return localOrRemote(competitionId,
                {
                    jooq.fetchPeriodsByCompetitionId(competitionId).collectList().flatMap { periods ->
                        jooq.fetchMatsByCompetitionId(competitionId).collectList().map { mats ->
                            ScheduleDTO()
                                    .setId(competitionId)
                                    .setPeriods(periods.toTypedArray())
                                    .setMats(mats.toTypedArray())
                        }
                    }
                },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/schedule?competitionId=$competitionId", ScheduleDTO::class.java).toMonoOrEmpty()
                })
    }


    fun getCategoryState(competitionId: String, categoryId: String): CategoryStateDTO? {
        log.info("Getting state for category $categoryId")
        return localOrRemote(competitionId, {
            jooq.fetchCategoryStateByCompetitionIdAndCategoryId(competitionId, categoryId)
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId", CategoryStateDTO::class.java).toMonoOrEmpty()
                })
    }


    fun getMats(competitionId: String, periodId: String): Array<MatDescriptionDTO>? {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return localOrRemote(competitionId, {
            jooq.fetchMatsByCompetitionIdAndPeriodId(competitionId, periodId, getFightStartTimes = false)
                    .collectList().map { mats ->
                        mats.toTypedArray()
                    }
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/mats?competitionId=$competitionId&periodId=$periodId", Array<MatDescriptionDTO>::class.java).toMonoOrEmpty()
                })
    }


    fun getMatFights(competitionId: String, matId: String, maxResults: Long): FightsWithCompetitors? {
        log.info("Getting fights for competition $competitionId and mat $matId")
        return localOrRemote(competitionId, {
            jooq.topMatFights(maxResults, competitionId, matId, FightsService.notFinishedStatuses)
                    .collectList().flatMap { fights ->
                        val competitorIds = fights.flatMap { it.scores.orEmpty().toList() }.mapNotNull { it.competitorId }
                        jooq.fetchCompetitorsByIds(competitorIds, competitionId).collectList()
                                .map { cmps ->
                                    FightsWithCompetitors()
                                            .setFights(fights.toTypedArray())
                                            .setCompetitors(cmps.toTypedArray())
                                }
                    }
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults", FightsWithCompetitors::class.java).toMonoOrEmpty()
        })
    }

    fun getStageFights(competitionId: String, stageId: String): Array<FightDescriptionDTO>? {
        log.info("Getting fights for stage $stageId")
        return localOrRemote(competitionId, {
            jooq.fetchFightsByStageId(competitionId, stageId).collectList().map { it.toTypedArray() }
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$stageId", Array<FightDescriptionDTO>::class.java).toMonoOrEmpty()
        })
    }

    fun getFight(competitionId: String, fightId: String): FightDescriptionDTO? {
        log.info("Getting fight for id $fightId")
        return localOrRemote(competitionId, {
            jooq.fetchFightById(competitionId, fightId)
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$fightId", FightDescriptionDTO::class.java).toMonoOrEmpty()
        })

    }

    fun getStages(competitionId: String, categoryId: String): Array<StageDescriptorDTO>? {
        log.info("Getting stages for $categoryId")
        return localOrRemote(competitionId, {
            jooq.fetchStagesForCategory(competitionId, categoryId).collectList().map { it.toTypedArray() }
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stages?competitionId=$competitionId&categoryId=$categoryId", Array<StageDescriptorDTO>::class.java).toMonoOrEmpty()
        })
    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        return localOrRemote(competitionId, {
            jooq.fetchCategoryStatesByCompetitionId(competitionId).collectList().map { it.toTypedArray() }
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/categories?competitionId=$competitionId", Array<CategoryStateDTO>::class.java).toMonoOrEmpty()
        }) ?: emptyArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardStateDTO? {
        return localOrRemote(competitionId, {
            jooq.fetchPeriodsByCompetitionId(competitionId).collectList().map { periods ->
                CompetitionDashboardStateDTO()
                        .setCompetitionId(competitionId)
                        .setPeriods(periods.toTypedArray())
            }.flatMap { state ->
                jooq.fetchMatsByCompetitionId(competitionId).collectList()
                        .map { mats -> state.setMats(mats.toTypedArray()) } }
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/dashboardstate?competitionId=$competitionId", CompetitionDashboardStateDTO::class.java).toMonoOrEmpty()
        })
    }

    fun getFightIdsByCategoryIds(competitionId: String): Map<String, Array<String>> {
        fun getLinkedHashMapType(): ParameterizedTypeReference<LinkedHashMap<String, Array<String>>> {
            return object : ParameterizedTypeReference<LinkedHashMap<String, Array<String>>>() {}
        }
        return localOrRemote(competitionId, {
            jooq.getCategoryIdsForCompetition(competitionId).flatMap { id ->
                jooq.getFightIdsForCategory(id).collectList().map { id to it.toTypedArray() }
            }.collectList().map { it.toMap() }
        }, { _, restTemplate, urlPrefix ->
            val typeRef = getLinkedHashMapType()
            val respEntity = restTemplate.exchange("$urlPrefix/api/v1/store/fightsbycategories?competitionId=$competitionId", HttpMethod.GET, HttpEntity.EMPTY, typeRef)
            val body = if (respEntity.statusCode == HttpStatus.OK) {
                respEntity.body
            } else {
                throw RestClientResponseException("Error while getting FightIdsByCategoryIds", respEntity.statusCodeValue, respEntity.statusCode.reasonPhrase, respEntity.headers, null, StandardCharsets.UTF_8)
            }
            body.toMonoOrEmpty()
        }) ?: emptyMap()
    }
}