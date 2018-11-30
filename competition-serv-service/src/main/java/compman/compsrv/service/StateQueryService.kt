package compman.compsrv.service

import compman.compsrv.cluster.ClusterSession
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.HostStoreInfo
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.schedule.Schedule
import io.scalecube.cluster.Member
import org.apache.kafka.streams.state.HostInfo
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate

class StateQueryService(private val clusterSession: ClusterSession, clusterConfigurationProperties: ClusterConfigurationProperties) {

    companion object {
        private val log = LoggerFactory.getLogger(StateQueryService::class.java)
    }

    private val hostInfo: HostInfo

    init {
        val advHost = clusterConfigurationProperties.advertisedHost
        val advPort = clusterConfigurationProperties.advertisedPort

        hostInfo = HostInfo(advHost, advPort)
    }

    private fun thisHost(hostStoreInfo: HostStoreInfo): Boolean {
        return hostInfo.host() == hostStoreInfo.host && hostInfo.port() == hostStoreInfo.port
    }

    private fun thisHost(hostStoreInfo: Member): Boolean {
        return hostInfo.host() == hostStoreInfo.address().host() && hostInfo.port() == hostStoreInfo.address().port()
    }

    private fun clusterSession() = clusterSession
    fun getCompetitionProperties(competitionId: String?): CompetitionState? {
        return if (competitionId != null) {
            clusterSession().getCompetitionProperties(competitionId)
        } else {
            null
        }
    }

    fun getSchedule(competitionId: String?): Schedule? {
        return if (competitionId != null) {
            clusterSession().getCompetitionProperties(competitionId)?.schedule
        } else {
            null
        }
    }

    fun getCategoryState(competitionId: String, categoryId: String): CategoryState? {
        val zks = clusterSession()
        log.info("Getting state for category $categoryId")
        return zks.getCompetitionProperties(competitionId)?.categories?.find { it.id == categoryId }
    }


    fun getCategories(competitionId: String): Array<CategoryDTO> {
        val zks = clusterSession()
        val categories = zks.getCompetitionProperties(competitionId)?.categories ?: emptyList()
        return categories
                .filter { !it.id.isBlank() }
                .mapNotNull { getCategoryState(competitionId, it.id) }
                .map { CategoryDTO(it) }.toTypedArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardState? {
        val zks = clusterSession()
        return zks.getCompetitionProperties(competitionId)?.dashboardState
    }
}