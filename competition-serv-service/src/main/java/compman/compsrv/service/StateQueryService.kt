package compman.compsrv.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import compman.compsrv.cluster.ClusterMember
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.model.PageResponse
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.RocksDBRepository
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.toMonoOrEmpty
import io.scalecube.net.Address
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.max

@Component
class StateQueryService(
    clusterOperationsProvider: ObjectProvider<ClusterOperations>,
    private val webClient: WebClient,
    rocksDBRepository: RocksDBRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }


    private val rocksDbOperations = rocksDBRepository.getOperations()

    private val clusterOperations = clusterOperationsProvider.ifAvailable

    fun getClusterInfo(): Flux<ClusterMember> = clusterOperations?.getClusterMembers() ?: Flux.empty()

    fun CompetitorDTO.qualifies(searchString: String) =
        firstName.contains(searchString, ignoreCase = true) ||
                lastName.contains(searchString, ignoreCase = true)

    private fun getLocalCompetitors(
        competitionId: String,
        categoryId: String?,
        searchString: String?,
        pageSize: Int,
        page: Int
    ): PageResponse<CompetitorDTO> {
        val pageNumber = max(page - 1, 0)
        val competition = rocksDbOperations.getCompetition(competitionId)
        return if (!searchString.isNullOrBlank()) {
            if (categoryId.isNullOrBlank()) {
                val categories = rocksDbOperations.getCategories(competition.categories.toList())
                val count = categories.fold(0) { acc, category -> acc + category.numberOfCompetitors }
                val competitors = rocksDbOperations.getCompetitionCompetitors(competitionId)
                PageResponse(competitionId, count.toLong(), page, competitors.asSequence().filter { it.competitorDTO.qualifies(searchString) }
                    .drop(pageSize * pageNumber).take(pageSize).map { it.competitorDTO }.toList().toTypedArray())
            } else {
                val category = rocksDbOperations.getCategory(categoryId)
                val count = category.numberOfCompetitors
                val competitors = rocksDbOperations.getCompetitors(category.competitors.toList())
                PageResponse(competitionId, count.toLong(), page, competitors.asSequence().filter { it.competitorDTO.qualifies(searchString) }
                    .drop(pageSize * pageNumber).take(pageSize).map { it.competitorDTO }.toList().toTypedArray())
            }
        } else {
            if (categoryId.isNullOrBlank()) {
                val categories = rocksDbOperations.getCategories(competition.categories.toList())
                val count = categories.fold(0) { acc, category -> acc + category.numberOfCompetitors }
                val competitors = rocksDbOperations.getCompetitionCompetitors(competitionId)
                PageResponse(competitionId, count.toLong(), page, competitors.asSequence().drop(pageSize * pageNumber).let { if (pageSize > 0) it.take(pageSize) else it }.map { it.competitorDTO }.toList().toTypedArray())
            } else {
                val category = rocksDbOperations.getCategory(categoryId)
                val count = category.numberOfCompetitors
                val competitors = rocksDbOperations.getCompetitors(category.competitors.toList())
                PageResponse(competitionId, count.toLong(), page, competitors.asSequence().drop(pageSize * pageNumber).let { if (pageSize > 0) it.take(pageSize) else it }.map { it.competitorDTO }.toList().toTypedArray())
            }
        }
    }

    fun getCompetitors(
        competitionId: String,
        categoryId: String?,
        searchString: String?,
        pageSize: Int,
        pageNumber: Int
    ): Mono<PageResponse<CompetitorDTO>> {
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
                restTemplate.get()
                    .uri(uri + queryParams.toString())
                    .retrieve()
                    .toEntity(typeRef)
                    .flatMap { respEntity ->
                        if (respEntity.statusCode == HttpStatus.OK) {
                            Mono.justOrEmpty(respEntity.body)
                        } else {
                            Mono.error(
                                RestClientResponseException(
                                    "Error while getting competitors",
                                    respEntity.statusCodeValue,
                                    respEntity.statusCode.reasonPhrase,
                                    respEntity.headers,
                                    null,
                                    StandardCharsets.UTF_8
                                )
                            )
                        }
                    }
            })
    }

    fun getCompetitor(competitionId: String, fighterId: String): Mono<CompetitorDTO> = localOrRemote(competitionId, {
        wrapBlocking {
            rocksDbOperations.getCompetitor(fighterId).competitorDTO
        }
    },
        { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId")
                .retrieve()
                .bodyToMono(CompetitorDTO::class.java)
//                restTemplate.getForObject("$urlPrefix/api/v1/store/competitor?competitionId=$competitionId&fighterId=$fighterId", CompetitorDTO::class.java).toMonoOrEmpty()
        })

    private fun <T> wrapBlocking(block: () -> T): Mono<T> =
        Mono.fromCallable {
            block()
        }.subscribeOn(Schedulers.boundedElastic())


    fun getFightResultOptions(
        competitionId: String,
        categoryId: String,
        fightId: String
    ): Mono<Array<FightResultOptionDTO>> = localOrRemote(competitionId, {
        wrapBlocking {
            val fight = rocksDbOperations.getFight(fightId)
            val category = rocksDbOperations.getCategory(categoryId)
            fight.stageId?.let { id ->
                val stage = category.stages.getValue(id)
                stage.dto.stageResultDescriptor.fightResultOptions
            } ?: emptyArray()
        }
    },
        { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/fightresultoptions?competitionId=$competitionId&fightId=$fightId&categoryId=$categoryId")
                .retrieve()
                .bodyToMono(Array<FightResultOptionDTO>::class.java)
        })

    fun getCompetitionInfoTemplate(competitionId: String): Mono<ByteArray> {
        return localOrRemote(competitionId,
            {
                wrapBlocking {
                    val competition = rocksDbOperations.getCompetition(competitionId)
                    competition.competitionInfoTemplate
                }
            },
            { _, _, url ->
                webClient.get()
                    .uri("$url/api/v1/store/infotemplate?competitionId=$competitionId")
                    .retrieve()
                    .bodyToMono(ByteArray::class.java)
            }
        )
    }

    fun getRegistrationInfo(competitionId: String): Mono<RegistrationInfoDTO> {
        return localOrRemoteIo(competitionId, {
            wrapBlocking {
                val competition = rocksDbOperations.getCompetition(competitionId)
                competition.registrationInfo
            }
        },
            { address, _, urlPrefix ->
                val url = "$urlPrefix/api/v1/store/reginfo?competitionId=$competitionId"
                log.info("Doing a remote request to address $address, url=$url")
                webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(RegistrationInfoDTO::class.java)
            })
    }

    fun getCompetitionProperties(competitionId: String): Mono<CompetitionPropertiesDTO> {
        return localOrRemoteIo(competitionId,
            {
                wrapBlocking {
                    val competition = rocksDbOperations.getCompetition(competitionId)
                    competition.properties
                }
            },
            { address, _, urlPrefix ->
                val url = "$urlPrefix/api/v1/store/comprops?competitionId=$competitionId"
                log.info("Doing a remote request to address $address, url=$url")
                webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(CompetitionPropertiesDTO::class.java)
            }
        )
    }

    fun <T> localOrRemoteIo(
        competitionId: String?,
        ifLocal: () -> Mono<T>,
        ifRemote: (instanceAddress: Address, webClient: WebClient, urlPrefix: String) -> Mono<T>
    ): Mono<T> =
        Either.fromNullable(clusterOperations).flatMap { ops ->
            Either.fromNullable(competitionId).map { id ->
                val instanceAddress = ops.findProcessingMember(id)
                instanceAddress.filter { it != null }.flatMap { address ->
                    ops.invalidateMemberForCompetitionId(id)
                    if (ops.isLocal(address!!)) {
                        log.info("Competition $competitionId is processed locally. Starting executing the logic.")
                        ifLocal()
                    } else {
                        log.debug("Competition $competitionId is processed by $address")
                        ifRemote(address, webClient, ops.getUrlPrefix(address.host(), address.port()))
                    }
                }.retryBackoff(3, Duration.ofMillis(10))
            }
        }.getOrElse { Mono.empty() }

    fun <T> localOrRemote(
        competitionId: String?,
        ifLocal: () -> Mono<T>,
        ifRemote: (instanceAddress: Address, webClient: WebClient, urlPrefix: String) -> Mono<T>
    ): Mono<T> {
        return localOrRemoteIo(competitionId, ifLocal, ifRemote)
    }

    fun getSchedule(competitionId: String): Mono<ScheduleDTO> {
        return localOrRemote(competitionId,
            {
                wrapBlocking {
                    val competition = rocksDbOperations.getCompetition(competitionId)
                    ScheduleDTO().setId(competitionId).setMats(competition.mats)
                        .setPeriods(competition.periods)
                }

            },
            { _, _, urlPrefix ->
                webClient.get()
                    .uri("$urlPrefix/api/v1/store/schedule?competitionId=$competitionId")
                    .retrieve()
                    .bodyToMono(ScheduleDTO::class.java)
            })
    }


    fun getCategoryState(competitionId: String, categoryId: String): Mono<CategoryStateDTO> {
        log.info("Getting state for category $categoryId")
        return localOrRemote(competitionId, {
            wrapBlocking {
                val category = rocksDbOperations.getCategory(categoryId)
                CategoryStateDTO().setCompetitionId(competitionId).setCategory(category.descriptor)
                    .setFightsNumber(category.stages.values.flatMap { it.fights.toList() }.size)
                    .setNumberOfCompetitors(category.numberOfCompetitors)
            }
        },
            { _, _, urlPrefix ->
                webClient.get()
                    .uri("$urlPrefix/api/v1/store/categorystate?competitionId=$competitionId&categoryId=$categoryId")
                    .retrieve()
                    .bodyToMono(CategoryStateDTO::class.java)
            })
    }


    fun getMats(competitionId: String, periodId: String): Mono<Array<MatDescriptionDTO>> {
        log.info("Getting mats for competition $competitionId and period $periodId")
        return localOrRemote(competitionId, {
            wrapBlocking {
                val competition = rocksDbOperations.getCompetition(competitionId)
                competition.mats
            }

        },
            { _, _, urlPrefix ->
                webClient.get()
                    .uri("$urlPrefix/api/v1/store/mats?competitionId=$competitionId&periodId=$periodId")
                    .retrieve()
                    .bodyToMono(Array<MatDescriptionDTO>::class.java)
            })
    }


    private fun hasEnoughCompetitors(fight: FightDescriptionDTO): Boolean {
        val scoresSize = fight.scores?.filter { !it.competitorId.isNullOrBlank() }?.size ?: 0
        return scoresSize > 1
    }

    fun getMatFights(competitionId: String, matId: String, maxResults: Long): Mono<FightsWithCompetitors> {
        log.info("Getting fights for competition $competitionId and mat $matId")
        return localOrRemote(competitionId, {
            wrapBlocking {
                val ff = rocksDbOperations.getCompetitionFights(competitionId)
                val k = ff.asSequence()
                    .filter {
                        it.mat?.id == matId && !FightsService.finishedStatuses.contains(it.status) && hasEnoughCompetitors(
                            it
                        )
                    }
                    .sortedBy { it.startTime }
                    .map { f ->
                        f to rocksDbOperations.getCompetitors(f.scores.mapNotNull { it.competitorId })
                    }
                    .take(maxResults.toInt())
                    .fold(listOf<FightDescriptionDTO>() to listOf<CompetitorDTO>()) { acc, pair ->
                        (acc.first + pair.first) to (acc.second + pair.second.map { it.competitorDTO })
                    }
                FightsWithCompetitors()
                    .setFights(k.first.toTypedArray())
                    .setCompetitors(k.second.toTypedArray())
            }
        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/matfights?competitionId=$competitionId&matId=$matId&maxResults=$maxResults")
                .retrieve()
                .bodyToMono(FightsWithCompetitors::class.java)
        })
    }

    fun getStageFights(competitionId: String, categoryId: String, stageId: String): Mono<Array<FightDescriptionDTO>> {
        log.info("Getting fights for stage $stageId")
        return localOrRemote(competitionId, {
            wrapBlocking {
                val category = rocksDbOperations.getCategory(categoryId)
                val fights = category.stages.getValue(stageId).fights
                rocksDbOperations.getFights(fights.toList()).toTypedArray()
            }

        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$stageId&categoryId=$categoryId")
                .retrieve()
                .bodyToMono(Array<FightDescriptionDTO>::class.java)
        })
    }

    fun getFight(competitionId: String, categoryId: String, fightId: String): Mono<FightDescriptionDTO> {
        log.info("Getting fight for id $fightId")
        return localOrRemote(competitionId, {
            wrapBlocking {
                rocksDbOperations.getFight(fightId)
            }
        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/stagefights?competitionId=$competitionId&stageId=$fightId&categoryId=$categoryId")
                .retrieve()
                .bodyToMono(FightDescriptionDTO::class.java)
        })

    }

    fun getStages(competitionId: String, categoryId: String): Mono<Array<StageDescriptorDTO>> {
        log.info("Getting stages for $categoryId")
        return localOrRemote(competitionId, {
            wrapBlocking {
                val category = rocksDbOperations.getCategory(categoryId)
                category.stages.values.map { it.dto }.toTypedArray()
            }
        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/stages?competitionId=$competitionId&categoryId=$categoryId")
                .retrieve()
                .bodyToMono(Array<StageDescriptorDTO>::class.java)
        })
    }


    fun getCategories(competitionId: String): Mono<Array<CategoryStateDTO>> {
        return localOrRemote(competitionId, {
            wrapBlocking {
                val competition = rocksDbOperations.getCompetition(competitionId)
                rocksDbOperations.getCategories(competition.categories.toList()).map {
                    CategoryStateDTO().setId(it.id).setNumberOfCompetitors(it.numberOfCompetitors)
                        .setFightsNumber(it.stages.values.flatMap { stage -> stage.fights.toList() }.size).setCompetitionId(competitionId).setCategory(it.descriptor)
                }.toTypedArray()
            }
        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/categories?competitionId=$competitionId")
                .retrieve()
                .bodyToMono(Array<CategoryStateDTO>::class.java)
        })
    }

    fun getDashboardState(competitionId: String): Mono<CompetitionDashboardStateDTO> {
        return localOrRemote(competitionId, {
            wrapBlocking {
                val competition = rocksDbOperations.getCompetition(competitionId)
                CompetitionDashboardStateDTO().setCompetitionId(competitionId).setMats(competition.mats)
                    .setPeriods(competition.periods)
            }
        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/dashboardstate?competitionId=$competitionId")
                .retrieve()
                .bodyToMono(CompetitionDashboardStateDTO::class.java)
        })
    }

    fun getFightIdsByCategoryIds(competitionId: String): Mono<Map<String, Array<String>>> {
        fun getLinkedHashMapType(): ParameterizedTypeReference<LinkedHashMap<String, Array<String>>> {
            return object : ParameterizedTypeReference<LinkedHashMap<String, Array<String>>>() {}
        }
        return localOrRemote(competitionId, {
            wrapBlocking {
                val fights = rocksDbOperations.getCompetitionFights(competitionId)
                java.util.LinkedHashMap(fights.groupBy { it.categoryId }.mapValues { e -> e.value.map { it.id }.toTypedArray() })
            }
        }, { _, _, urlPrefix ->
            webClient.get()
                .uri("$urlPrefix/api/v1/store/fightsbycategories?competitionId=$competitionId")
                .retrieve()
                .bodyToMono(getLinkedHashMapType())
        }).map { it as Map<String, Array<String>> }
    }
}