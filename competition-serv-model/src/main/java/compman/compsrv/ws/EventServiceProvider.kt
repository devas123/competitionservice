package compman.compsrv.ws

import compman.compsrv.model.es.events.EventHolder
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces

@Path("/events/")
@Produces("application/json")
@Consumes("application/json")
interface EventServiceProvider {

    @POST
    @Path("/accept")
    fun acceptEvent(event: EventHolder)

}