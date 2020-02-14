package compman.compsrv.service

import arrow.core.Either
import arrow.fx.IO
import com.compmanager.compservice.jooq.tables.*
import com.compmanager.compservice.jooq.tables.daos.*
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.PageResponse
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatStateDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import compman.compsrv.repository.JooqQueries
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
import javax.transaction.Transactional
import kotlin.math.max

@Component
class StateQueryService(private val clusterSession: ClusterSession,
                        restTemplateBuilder: RestTemplateBuilder,
                        private val periodDao: SchedulePeriodDao,
                        private val jooq: JooqQueries,
                        private val registrationInfoDao: RegistrationInfoDao,
                        private val fightStartTimesDao: FightStartTimesDao,
                        private val staffDao: CompetitionPropertiesStaffIdsDao,
                        private val promoCodeDao: PromoCodeDao,
                        private val matScheduleContainerDao: MatScheduleContainerDao,
                        private val scheduleEntriesDao: ScheduleEntriesDao,
                        private val periodPropertiesDao: SchedulePeriodPropertiesDao,
                        private val competitionPropertiesCrudRepository: CompetitionPropertiesDao,
                        private val matDescriptionCrudRepository: MatDescriptionDao,
                        private val competitorCrudRepository: CompetitorDao) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }

    private val restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(10)).build()

    private fun getLocalCompetitors(competitionId: String, categoryId: String?, searchString: String?, pageSize: Int, page: Int): PageResponse<CompetitorDTO> {
        val pageNumber = max(page - 1, 0)
        val fromJoinedTables = jooq.competitorsQuery(competitionId)
        val count = jooq.competitorsCount(competitionId, categoryId)

        val prefetch = if (!searchString.isNullOrBlank()) {
            if (categoryId.isNullOrBlank()) {
                fromJoinedTables
                        .and(Competitor.COMPETITOR.FIRST_NAME.like(searchString).or(Competitor.COMPETITOR.LAST_NAME.like(searchString)))
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
                        .limit(pageSize)
            } else {
                fromJoinedTables
                        .and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(categoryId))
                        .and(Competitor.COMPETITOR.FIRST_NAME.like(searchString)
                                .or(Competitor.COMPETITOR.LAST_NAME.like(searchString)))
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
                        .limit(pageSize)
            }
        } else {
            if (categoryId.isNullOrBlank()) {
                fromJoinedTables
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
                        .limit(pageSize)
                        .offset(pageNumber * pageSize)
            } else {
                fromJoinedTables
                        .and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.equal(categoryId))
                        .orderBy(Competitor.COMPETITOR.FIRST_NAME, Competitor.COMPETITOR.LAST_NAME)
                        .limit(pageSize)
                        .offset(pageNumber * pageSize)
            }
        }
        val competitors = prefetch.fetch { rec ->
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


    fun getCompetitionInfoTemplate(competitionId: String?): ByteArray? {
        return localOrRemote(competitionId,
                { competitionPropertiesCrudRepository.findById(competitionId)?.competitionInfoTemplate },
                { _, restTemplate, url ->
                    restTemplate.getForObject("$url/api/v1/store/infotemplate?competitionId=$competitionId", ByteArray::class.java)
                }
        )
    }

    fun getCompetitionProperties(competitionId: String): CompetitionPropertiesDTO? {
        return localOrRemote(competitionId,
                {
                    log.info("Getting competition properties id $competitionId")

                    val joinedColumnsFlat = jooq.getRegistrationGroupPeriodsQuery(competitionId)
                            .fetchSize(200)
                            .fetch()

                    val result = competitionPropertiesCrudRepository.findById(competitionId)
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

    fun <T> localOrRemote(competitionId: String?, ifLocal: () -> T?, ifRemote: (instanceAddress: Address, restTemplate: RestTemplate, urlPrefix: String) -> T?): T? {
        var k: Either<Throwable, T?> = Either.left(RuntimeException("Request failed for competition $competitionId, see logs for details. "))
        competitionId?.let { id ->
            var retries = 3
            do {
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
                    ScheduleDTO()
                            .setId(competitionId)
                            .setPeriods(periodDao.fetchByCompetitionId(competitionId).map { p ->
                                p.toDTO(scheduleEntriesDao.fetchByPeriodId(p.id)?.map { it.toDTO() }?.toTypedArray()!!,
                                        matScheduleContainerDao.fetchByPeriodId(p.id).map { mat ->
                                            mat.toDTO(fightStartTimesDao.fetchByMatScheduleId(mat.id).map {
                                                it.toDTO(mat.id)
                                            }.toTypedArray())
                                        }.toTypedArray())
                            }.toTypedArray())
                            .setScheduleProperties(SchedulePropertiesDTO()
                                    .setCompetitionId(competitionId)
                                    .setPeriodPropertiesList(periodPropertiesDao.fetchByCompetitionId(competitionId).map {
                                        it.toDTO(jooq.getCategoryIdsForSchedulePeriodProperties(competitionId!!, it.id).collectList().block()?.toTypedArray())
                                    }.toTypedArray()))
                },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/schedule?competitionId=$competitionId", ScheduleDTO::class.java)
                })
    }


    fun getCategoryState(competitionId: String, categoryId: String): CategoryStateDTO? {
        log.info("Getting state for category $categoryId")
        return localOrRemote(competitionId, {
            jooq.fetchCategoryStateByCompetitionIdAndCategoryId(competitionId, categoryId).block()
        },
                { _, restTemplate, urlPrefix ->
                    restTemplate.getForObject("$urlPrefix/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId", CategoryStateDTO::class.java)
                })
    }


    fun getMats(competitionId: String, periodId: String): List<MatStateDTO>? {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return localOrRemote(competitionId, {
            val mats = matDescriptionCrudRepository.fetchByDashboardPeriodId(periodId)
           mats?.map { mat ->
                val matDescrDto = mat.toDTO()
                val topFiveFights = jooq.topMatFights(5, competitionId, mat.id, FightsGenerateService.notFinishedStatuses)
                        .collectList().block() ?: emptyList()
                MatStateDTO()
                        .setMatDescription(matDescrDto)
                        .setNumberOfFights(jooq.fightsCountByMatId(competitionId, mat.id!!))
                        .setTopFiveFights(topFiveFights.toTypedArray())
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
            jooq.topMatFights(15, competitionId, matId, FightsGenerateService.notFinishedStatuses)
                    .collectList().block()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults", Array<FightDescriptionDTO>::class.java)?.toList()
                    ?: emptyList()
        })
    }

    @Transactional
    fun getStageFights(competitionId: String, stageId: String): Array<FightDescriptionDTO>? {
        log.info("Getting competitors for stage $stageId")
        return localOrRemote(competitionId, {
           jooq.fetchFightsByStageId(competitionId, stageId).collectList().block()?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$stageId", Array<FightDescriptionDTO>::class.java)
        })

    }

    @Transactional
    fun getStages(competitionId: String, categoryId: String): Array<StageDescriptorDTO>? {
        log.info("Getting stages for $categoryId")
        return localOrRemote(competitionId, {
           jooq.fetchStagesForCategory(competitionId, categoryId).collectList().block()?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/stages?competitionId=$competitionId&categoryId=$categoryId", Array<StageDescriptorDTO>::class.java)
        })

    }


    fun getCategories(competitionId: String): Array<CategoryStateDTO> {
        return localOrRemote(competitionId, {
            jooq.fetchCategoryStatesByCompetitionId(competitionId).collectList().block()?.toTypedArray()
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/categories?competitionId=$competitionId", Array<CategoryStateDTO>::class.java)
        }) ?: emptyArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardStateDTO? {
        return localOrRemote(competitionId, {
            val periods = jooq.fetchDashboardPeriodsByCompeititonId(competitionId).collectList().block()?.toTypedArray()
            CompetitionDashboardStateDTO()
                    .setCompetitionId(competitionId)
                    .setPeriods(periods)
        }, { _, restTemplate, urlPrefix ->
            restTemplate.getForObject("$urlPrefix/api/v1/store/dashboardstate?competitionId=$competitionId", CompetitionDashboardStateDTO::class.java)
        })
    }
}