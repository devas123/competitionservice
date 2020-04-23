package compman.compsrv.service

import arrow.core.Option
import arrow.fx.IO
import arrow.fx.fix
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
                        private val staffDao: CompetitionPropertiesStaffIdsDao,
                        private val promoCodeDao: PromoCodeDao,
                        private val competitionPropertiesDao: CompetitionPropertiesDao,
                        private val fightResultOptionDao: FightResultOptionDao,
                        private val fightDescriptionDao: FightDescriptionDao,
                        private val competitorCrudRepository: CompetitorDao) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }

    private val timeout = Duration.ofSeconds(10)
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
                    getLocalCompetitors(competitionId, categoryId, searchString, pageSize, pageNumber)
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

    fun getCompetitor(competitionId: String, fighterId: String) = localOrRemote(competitionId, {
        competitorCrudRepository.findById(fighterId)?.toDTO(jooq.getCompetitorCategories(competitionId, fighterId).toTypedArray())
    },
            { _, restTemplate, urlPrefix ->
                restTemplate.getForObject("$urlPrefix/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId", CompetitorDTO::class.java)
            })

    fun getFightResultOptions(competitionId: String, fightId: String) = localOrRemote(competitionId, {
        val fight = fightDescriptionDao.findById(fightId)
        fight?.let { fightResultOptionDao.fetchByStageId(it.stageId)?.map { fr -> fr.toDTO() } }?.toTypedArray()
    },
            { _, restTemplate, urlPrefix ->
                restTemplate.getForObject("$urlPrefix/api/v1/store/fightresultoptions?competitionId=$competitionId&fightId=$fightId", Array<FightResultOptionDTO>::class.java)
            })


    fun getCompetitionInfoTemplate(competitionId: String?): ByteArray? {
        return localOrRemote(competitionId,
                { competitionPropertiesDao.findById(competitionId)?.competitionInfoTemplate },
                { _, restTemplate, url ->
                    restTemplate.getForObject("$url/api/v1/store/infotemplate?competitionId=$competitionId", ByteArray::class.java)
                }
        )
    }

    fun getCompetitionProperties(competitionId: String): IO<Option<CompetitionPropertiesDTO>> {
        return localOrRemoteIo(competitionId,
                {
                    log.info("Getting competition properties id $competitionId")
                    val joinedColumnsFlat = jooqQueryProvider.getRegistrationGroupPeriodsQuery(competitionId).fetch()
                    val result = competitionPropertiesDao.findById(competitionId)
                            ?.toDTO(staffDao.fetchByCompetitionPropertiesId(competitionId)?.map { it.staffId }?.toTypedArray(),
                                    promoCodeDao.fetchByCompetitionId(competitionId)?.map { it.toDTO() }?.toTypedArray())
                            { regInfoId ->
                                registrationInfoDao.findById(regInfoId)?.let {
                                    val byGroups = joinedColumnsFlat.intoGroups(RegistrationGroup.REGISTRATION_GROUP.ID)
                                    val byPeriods = joinedColumnsFlat.intoGroups(RegistrationPeriod.REGISTRATION_PERIOD.ID)
                                    val registrationCategories = byGroups.mapValues { e ->
                                        e.value.intoArray(RegistrationGroupCategories.REGISTRATION_GROUP_CATEGORIES.CATEGORY_ID).filterNotNull()
                                    }
                                    val registrationPeriodIds = byGroups.mapValues { e ->
                                        e.value.intoArray(RegistrationPeriod.REGISTRATION_PERIOD.ID).filterNotNull()
                                    }

                                    val regGroupIds = byPeriods.mapValues { e ->
                                        e.value.intoArray(RegistrationGroup.REGISTRATION_GROUP.ID).filterNotNull()
                                    }

                                    RegistrationInfoDTO()
                                            .setId(regInfoId)
                                            .setRegistrationOpen(it.registrationOpen)
                                            .setRegistrationPeriods(
                                                    joinedColumnsFlat.map { rec ->
                                                        val id = rec[RegistrationPeriod.REGISTRATION_PERIOD.ID]
                                                        RegistrationPeriodDTO()
                                                                .setCompetitionId(competitionId)
                                                                .setEnd(rec[RegistrationPeriod.REGISTRATION_PERIOD.END_DATE]?.toInstant())
                                                                .setStart(rec[RegistrationPeriod.REGISTRATION_PERIOD.START_DATE]?.toInstant())
                                                                .setId(id)
                                                                .setName(rec[RegistrationPeriod.REGISTRATION_PERIOD.NAME])
                                                                .setRegistrationGroupIds(regGroupIds[id]?.toTypedArray())
                                                    }?.toTypedArray()
                                            )
                                            .setRegistrationGroups(
                                                    joinedColumnsFlat.map { rec ->
                                                        val id = rec[RegistrationGroup.REGISTRATION_GROUP.ID]
                                                        RegistrationGroupDTO()
                                                                .setRegistrationInfoId(regInfoId)
                                                                .setCategories(registrationCategories[id]?.toTypedArray())
                                                                .setDefaultGroup(rec[RegistrationGroup.REGISTRATION_GROUP.DEFAULT_GROUP])
                                                                .setId(id)
                                                                .setRegistrationFee(rec[RegistrationGroup.REGISTRATION_GROUP.REGISTRATION_FEE])
                                                                .setRegistrationPeriodIds(registrationPeriodIds[id]?.toTypedArray())
                                                    }?.toTypedArray()
                                            )
                                }
                            }
                    log.info("Found competition properties: $result")
                    result
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

    fun <T> localOrRemoteIo(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> T?) =
            competitionId?.let { id ->
                val instanceAddress = clusterSession.findProcessingMember(id)
                instanceAddress.flatMap { address ->
                    (1..3).toList()
                            .fold(IO.raiseError<T?>(RuntimeException("Request failed for competition $id, see logs for details. "))) { io, i ->
                                io.redeem({
                                    log.debug("Attempt $i")
                                    clusterSession.invalidateMemberForCompetitionId(id)
                                    if (clusterSession.isLocal(address)) {
                                        log.info("Competition $competitionId is processed locally. Starting executing the logic.")
                                        ifLocal()
                                    } else {
                                        log.debug("Competition $competitionId is processed by $address")
                                        ifRemote(address, restTemplate, clusterSession.getUrlPrefix(address.host(), address.port()))
                                    }
                                }, { it }) }.fix()
                }
            }?.map { Option.fromNullable(it) } ?: IO.just(Option.empty())


    fun <T> localOrRemote(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> T?): T? {
        return localOrRemoteIo(competitionId, ifLocal, ifRemote).attempt().unsafeRunSync().fold({ throw it }, { it.orNull() })
    }

    fun getSchedule(competitionId: String): ScheduleDTO? {
        return localOrRemote(competitionId,
                {
                    ScheduleDTO()
                            .setId(competitionId)
                            .setPeriods(jooq.fetchPeriodsByCompetitionId(competitionId).collectList().block(timeout)?.toTypedArray())
                            .setMats(jooq.fetchMatsByCompetitionId(competitionId, getFightStartTimes = false).collectList().block(timeout)?.toTypedArray())
                },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/schedule?competitionId=$competitionId", ScheduleDTO::class.java)
                })
    }


    fun getCategoryState(competitionId: String, categoryId: String): CategoryStateDTO? {
        log.info("Getting state for category $categoryId")
        return localOrRemote(competitionId, {
            jooq.fetchCategoryStateByCompetitionIdAndCategoryId(competitionId, categoryId).block(timeout)
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId", CategoryStateDTO::class.java)
                })
    }


    fun getMats(competitionId: String, periodId: String): Array<MatDescriptionDTO>? {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return localOrRemote(competitionId, {
            val mats = jooq.fetchMatsByCompetitionIdAndPeriodId(competitionId, periodId, getFightStartTimes = false)
                    .collectList().block(timeout)
            mats?.toTypedArray()
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/mats?competitionId=$competitionId&periodId=$periodId", Array<MatDescriptionDTO>::class.java)
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
                    }.block(timeout)
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults", FightsWithCompetitors::class.java)
        })
    }

    fun getStageFights(competitionId: String, stageId: String): Array<FightDescriptionDTO>? {
        log.info("Getting competitors for stage $stageId")
        return localOrRemote(competitionId, {
            jooq.fetchFightsByStageId(competitionId, stageId).collectList().block(timeout)?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$stageId", Array<FightDescriptionDTO>::class.java)
        })

    }

    fun getStages(competitionId: String, categoryId: String): Array<StageDescriptorDTO>? {
        log.info("Getting stages for $categoryId")
        return localOrRemote(competitionId, {
            jooq.fetchStagesForCategory(competitionId, categoryId).collectList().block(timeout)?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stages?competitionId=$competitionId&categoryId=$categoryId", Array<StageDescriptorDTO>::class.java)
        })
    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        return localOrRemote(competitionId, {
            jooq.fetchCategoryStatesByCompetitionId(competitionId).collectList().block(timeout)?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/categories?competitionId=$competitionId", Array<CategoryStateDTO>::class.java)
        }) ?: emptyArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardStateDTO? {
        return localOrRemote(competitionId, {
            val periods = jooq.fetchPeriodsByCompetitionId(competitionId).collectList().block()?.toTypedArray()
            CompetitionDashboardStateDTO()
                    .setCompetitionId(competitionId)
                    .setPeriods(periods)
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/dashboardstate?competitionId=$competitionId", CompetitionDashboardStateDTO::class.java)
        })
    }

    @SuppressWarnings("UNCHECKED")
    fun getFightIdsByCategoryIds(competitionId: String): Map<String, Array<String>> {
        return localOrRemote(competitionId, {
            val categoryIds = jooq.getCategoryIdsForCompetition(competitionId).collectList().block(timeout).orEmpty()
            categoryIds.map { id ->
                id to (jooq.getFightIdsForCategory(id).collectList().block()?.toTypedArray() ?: emptyArray())
            }.toMap()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/fightsbycategories?competitionId=$competitionId", LinkedHashMap::class.java) as LinkedHashMap<String, Array<String>>
        }) ?: emptyMap()
    }
}