package compman.compsrv.model.es.events

/**
 * Created by ezabavno on 31.05.2017.
 */
class ChangeMatEvent {
    var fightId = ""
    var matId = ""
    var fightCompId = ""

    //returns
    var newFightCompId = null
    var fightsForUpdate = ArrayList<String>()
}