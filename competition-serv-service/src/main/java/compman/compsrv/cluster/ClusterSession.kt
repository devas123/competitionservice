package compman.compsrv.cluster

import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.HostStoreInfo
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.service.StateQueryService
import compman.compsrv.service.saga.SagaManager
import io.scalecube.cluster.Cluster
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate

class ClusterSession(private val clusterConfigurationProperties: ClusterConfigurationProperties,
                     private val restTemplate: RestTemplate,
                     private val cluster: Cluster) {
    private lateinit var leaderProcess: LeaderProcess
    lateinit var stateQueryService: StateQueryService
        private set

    fun getHostForCategory(categoryId: String): HostStoreInfo {
        TODO()
    }

    fun getHostForMat(matId: String): HostStoreInfo {
        TODO()
    }

    fun getCategoryState(categoryId: String): CategoryState? {
        TODO()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ClusterSession::class.java)
    }

    fun getCompetitionProperties(competitionId: String): CompetitionState? = leaderProcess.getCompetitionProperties(competitionId)

    private fun init() {
        stateQueryService = StateQueryService(this, restTemplate, clusterConfigurationProperties)
    }

    init {
        init()
    }

    fun getCategoriesForCompetition(competitionId: String) = leaderProcess.getCategories(competitionId)?.toTypedArray()
    fun getCompetitions(status: CompetitionStatus?, creatorId: String?): Array<CompetitionState> {
        return leaderProcess.getCompetitions(status, creatorId)
    }

    fun getDashboardState(competitionId: String) = leaderProcess.getDashboardState(competitionId)
}