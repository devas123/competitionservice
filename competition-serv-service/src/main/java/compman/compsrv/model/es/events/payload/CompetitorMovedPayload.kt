package compman.compsrv.model.es.events.payload

import compman.compsrv.model.competition.FightDescription

data class CompetitorMovedPayload(val updatedSourceFight: FightDescription, val updatedTargetFight: FightDescription)