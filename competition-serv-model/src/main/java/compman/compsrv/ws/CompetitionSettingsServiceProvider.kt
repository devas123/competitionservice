package compman.compsrv.ws

import compman.compsrv.model.competition.CompetitionProperties
import java.math.BigDecimal
import javax.ws.rs.*

@Path("/competitions/")
@Produces("application/json")
@Consumes("application/json")
interface CompetitionSettingsServiceProvider {

    @GET
    @Path("/coefficient")
    fun getCoefficient(@QueryParam("promo") promo: String, @QueryParam("competitionId") competitionId: String): BigDecimal?

    @GET
    @Path("/amount")
    fun getAmount(@QueryParam("competitionId") competitionId: String): BigDecimal?

    @GET
    @Path("/competitions")
    fun getCompetitions(): List<CompetitionProperties>?

    @GET
    @Path("/competitionsettings")
    fun getCompetitionSettings(@QueryParam("competitionId") competitionId: String): CompetitionProperties?

}