package compman.compsrv.model.es.events

import compman.compsrv.model.competition.Score

class ScoreUpdateEvent {
    var fightId: String = ""
    var scores: Score = Score(0,0,0,"")
}