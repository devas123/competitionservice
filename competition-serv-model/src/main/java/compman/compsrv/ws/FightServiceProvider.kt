package compman.compsrv.ws

import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.competition.GenerateAbsoluteMessage
import javax.ws.rs.*

@Path("/fights/")
@Produces("application/json")
@Consumes("application/json")
interface FightServiceProvider {

    @GET
    @Path("/getAll")
    fun getAllFights(@QueryParam("competitionId") competitionId: String): List<FightDescription>

    @GET
    @Path("/generateFights")
    fun generateFights(@QueryParam("competitionId") competitionId: String): List<FightDescription>?


    @POST
    @Path("/generateFightsForCategory")
    fun generateFightsForCategory(@QueryParam("competitionId") competitionId: String, category: Category): List<FightDescription>?

    @POST
    @Path("generateAbsoluteCategory")
    fun generateAbsoluteCategory(message: GenerateAbsoluteMessage): List<FightDescription>?

    @POST
    @Path("/saveBrackets")
    fun saveFights(fights: List<FightDescription>)

    @GET
    @Path("/getFightsForCategory")
    fun getFightsForCategory(@QueryParam("competitionId") competitionId: String, @QueryParam("categoryId") categoryId: String): List<FightDescription>?

    @POST
    @Path("/deleteFightsForCategory")
    fun deleteFightsForCategory(@QueryParam("competitionId") competitionId: String, categoryId: String)

    @POST
    @Path("/deleteAllFights")
    fun deleteAllFights(@QueryParam("competitionId") competitionId: String)

    @POST
    @Path("/updateFight")
    fun updateFight(fight: FightDescription)


}