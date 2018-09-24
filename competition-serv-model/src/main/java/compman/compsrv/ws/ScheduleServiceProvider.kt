package compman.compsrv.ws

import com.compmanager.service.ServiceException
import compman.compsrv.model.schedule.Schedule
import compman.compsrv.model.schedule.ScheduleProperties
import javax.ws.rs.*

@Path("/schedule/")
@Produces("application/json")
@Consumes("application/json")
interface ScheduleServiceProvider {

    @POST
    @Path("/saveSchedule")
    fun saveSchedule(schedule: Schedule)

    @GET
    @Path("/getSchedule")
    fun getSchedule( @QueryParam("categoryId") categoryId: String) : Schedule?

    @POST
    @Path("/generateSchedule")
    @Throws(ServiceException::class)
    fun generateSchedule(properties: ScheduleProperties): Schedule

}