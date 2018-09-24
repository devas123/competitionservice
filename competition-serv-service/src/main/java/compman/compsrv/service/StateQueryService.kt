package compman.compsrv.service

import compman.compsrv.cluster.ZookeeperSession
import compman.compsrv.config.ClusterConfigurationProperties
import compman.compsrv.kafka.HostStoreInfo
import compman.compsrv.model.competition.*
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.dto.CompetitionPropertiesDTO
import compman.compsrv.model.schedule.Schedule
import org.apache.kafka.streams.state.HostInfo
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.*
import javax.ws.rs.NotFoundException

class StateQueryService(private val zookeeperSession: ZookeeperSession, private val restTemplate: RestTemplate, clusterConfigurationProperties: ClusterConfigurationProperties) {

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


    fun doGetHostForCategory(decodedCatId: String) = try {
        val zks = getZookeeperSession()
        zks.getHostForCategory(decodedCatId)
    } catch (e: Exception) {
        log.warn("Could not find metadata for category $decodedCatId", e)
        null
    }

    fun doGetHostForMat(decodedMatId: String) = try {
        val zks = getZookeeperSession()
        zks.getHostForMat(decodedMatId)
    } catch (e: Exception) {
        log.warn("Could not find metadata for mat $decodedMatId", e)
        null
    }

    private fun getZookeeperSession() = zookeeperSession
    fun getCompetitionProperties(competitionId: String?): CompetitionProperties? {
        return if (competitionId != null) {
            getZookeeperSession().getCompetitionProperties(competitionId)
        } else {
            null
        }
    }

    fun getSchedule(competitionId: String?): Schedule? {
        return if (competitionId != null) {
            getZookeeperSession().getCompetitionProperties(competitionId)?.schedule
        } else {
            null
        }
    }

    fun getCategoryState(categoryId: String): CategoryState? {
        val zks = getZookeeperSession()
        log.info("Getting state for category $categoryId")
        val hostStoreInfo = doGetHostForCategory(categoryId)
        return if (hostStoreInfo != null && thisHost(hostStoreInfo)) {
            zks.getCategoryState(categoryId)
        } else if (hostStoreInfo != null) {
            val encodedCatId = Base64.getEncoder().encodeToString(categoryId.toByteArray(StandardCharsets.UTF_8))
            restTemplate.getForObject("http://${hostStoreInfo.host}:${hostStoreInfo.port}/competitions/cluster/store/categorystate?categoryId=$encodedCatId", CategoryState::class.java)
                    ?: throw RuntimeException("Could not get category state from $hostStoreInfo for category: $categoryId")
        } else {
            throw NotFoundException("Could not find store metadata for category $categoryId")
        }
    }

    fun getCompetitions(status: CompetitionStatus?, creatorId: String?): Array<CompetitionPropertiesDTO> {
        val zks = getZookeeperSession()
        return zks.getCompetitions(status, creatorId).map { CompetitionPropertiesDTO(it.setSchedule(null)) }.toTypedArray()

    }

    fun getCategories(competitionId: String): Array<CategoryDTO> {
        val zks = getZookeeperSession()
        val categories = zks.getCategoriesForCompetition(competitionId) ?: emptyArray()
        return categories
                .filter { !it.categoryId.isNullOrBlank() }
                .mapNotNull { getCategoryState(it.categoryId!!) }
                .map { CategoryDTO(it) }.toTypedArray()
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardState? {
        val zks = getZookeeperSession()
        return zks.getDashboardState(competitionId)
    }

    fun getMatState(matId: String): MatState? {
        val zks = getZookeeperSession()
        log.info("Getting mat state for mat $matId")
        val hostStoreInfo = doGetHostForMat(matId)
        return if (hostStoreInfo != null && thisHost(hostStoreInfo)) {
            zks.getMatState(matId)
        } else if (hostStoreInfo != null) {
            val encodedMatId = Base64.getEncoder().encodeToString(matId.toByteArray(StandardCharsets.UTF_8))
            restTemplate.getForObject("http://${hostStoreInfo.host}:${hostStoreInfo.port}/competitions/cluster/store/matstate?matId=$encodedMatId", MatState::class.java)
                    ?: throw RuntimeException("Could not get category state from $hostStoreInfo for category: $matId")
        } else {
            throw NotFoundException("Could not find store metadata for category $matId")
        }

    }
}