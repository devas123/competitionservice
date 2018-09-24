package compman.compsrv.ws

import com.compmanager.model.payment.UpdateStatusMessage
import com.compmanager.service.ServiceException
import compman.compsrv.model.competition.Competitor
import javax.ws.rs.*

@Path("/competitor/")
@Produces("application/json")
@Consumes("application/json")
interface CompetitorServiceProvider {

    @GET
    @Path("/get")
    fun get(@QueryParam("competitionId") competitionId: String, @QueryParam("id") id: String?): List<Competitor>

    @GET
    @Path("/get/confirmed")
    fun getConfirmedCompetitors(@QueryParam("competitionId") competitionId: String): List<Competitor>

    @GET
    @Path("/get/byacademy")
    fun getCompetitorsByAcademy(@QueryParam("competitionId") competitionId: String, @QueryParam("academyId") academyId: String?): List<Competitor>

    @GET
    @Path("/get/bycategory")
    fun getConfirmedCompetitorsByCategory(@QueryParam("competitionId") competitionId: String, @QueryParam("categoryId") categoryId: String): List<Competitor>



    @POST
    @Path("/update/status")
    @Throws(ServiceException::class)
    fun updatePaymentStatus(msg: UpdateStatusMessage)
}