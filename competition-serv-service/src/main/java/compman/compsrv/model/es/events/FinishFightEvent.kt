package compman.compsrv.model.es.events

import compman.compsrv.model.competition.FightResult

class FinishFightEvent {
    var fightId: String = ""
    var result: FightResult = FightResult("", false, "")
}