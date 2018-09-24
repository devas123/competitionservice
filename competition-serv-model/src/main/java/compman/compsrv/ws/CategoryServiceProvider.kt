package compman.compsrv.ws


import compman.compsrv.model.competition.AgeDivision
import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.Weight
import javax.ws.rs.*

@Path("/category")
@Produces("application/json")
@Consumes("application/json")
interface CategoryServiceProvider {

    @GET
    @Path("/get")
    fun getCategories(@QueryParam("ageDivision") age: String?, @QueryParam("gender") gender: String?, @QueryParam("competitionId") competitionId: String): List<Category>

    @GET
    @Path("/get/ages")
    fun getAgeDivisions(@QueryParam("gender") gender: String?, @QueryParam("competitionId") competitionId: String): List<AgeDivision>

    @GET
    @Path("/get/belts")
    fun getBeltsForGender(@QueryParam("gender") gender: String?, @QueryParam("competitionId") competitionId: String): Map<AgeDivision?, List<String?>>?

    @GET
    @Path("/get/getWeightsForDivisions")
    fun getWeightsForDivisions(@QueryParam("gender")gender: String, @QueryParam("competitionId") competitionId: String):  Map<String, List<Weight>>


}