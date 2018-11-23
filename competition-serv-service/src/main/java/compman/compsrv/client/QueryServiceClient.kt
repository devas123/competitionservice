package compman.compsrv.client

import compman.compsrv.model.competition.CompetitionStateSnapshot
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "query-service", url = "\${communication.query-service}")
@Component
interface QueryServiceClient {
    @RequestMapping(method = [(RequestMethod.GET)], value = ["/api/v1/competition/snapshot"], consumes = [(MediaType.APPLICATION_JSON_UTF8_VALUE)])
    fun getCompetitionStateSnapshot(@RequestParam("competitionId") competitionId: String): CompetitionStateSnapshot?
}