package compman.compsrv.model.es.events

class ErrorEvent {
    var exception: String? = ""
    var failedOn: EventHolder? = null
}