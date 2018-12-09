package compman.compsrv.model.es.events.payload

import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.competition.FightResult
import compman.compsrv.model.competition.FightStage
import compman.compsrv.model.competition.Score

class FightResultUpdateEvent {
    var fight: FightDescription? = null
    var result: FightResult? = FightResult("", false, "")
    var stage: FightStage? = FightStage.PENDING
    var scores: Array<Score>? = emptyArray()
}