package compman.compsrv.model.es.events.payload

import compman.compsrv.model.es.events.EventHolder

class ErrorEvent {
    var exception: String? = ""
    var failedOn: EventHolder? = null
}