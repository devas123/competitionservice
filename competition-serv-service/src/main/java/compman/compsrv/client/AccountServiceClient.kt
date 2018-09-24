package compman.compsrv.client

import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.Competitor
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "account-service", url = "\${communication.account-service}")
@Component
interface AccountServiceClient {
    @RequestMapping(method = [(RequestMethod.GET)], value = ["/category/get"], consumes = [(MediaType.APPLICATION_JSON_UTF8_VALUE)])
    fun getCategories(@RequestParam("ageDivision") age: String?, @RequestParam("gender") gender: String?, @RequestParam("competitionId") competitionId: String): List<Category>

    @RequestMapping(method = [(RequestMethod.GET)], value = ["/competitor/get/confirmed"], consumes = [(MediaType.APPLICATION_JSON_UTF8_VALUE)])
    fun getConfirmedCompetitors(@RequestParam("competitionId") competitionId: String): List<Competitor>

    @RequestMapping(method = [(RequestMethod.GET)], value = ["/competitor/get/bycategory"], consumes = [(MediaType.APPLICATION_JSON_UTF8_VALUE)])
    fun getConfirmedCompetitorsByCategory(@RequestParam("competitionId") competitionId: String, @RequestParam("categoryId") categoryId: String): List<Competitor>
}