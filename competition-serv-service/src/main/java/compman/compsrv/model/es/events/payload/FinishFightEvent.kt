package compman.compsrv.model.es.events.payload

import compman.compsrv.model.competition.FightResult

class FinishFightEvent {
    var fightId: String = ""
    var result: FightResult = FightResult("", false, "")
}