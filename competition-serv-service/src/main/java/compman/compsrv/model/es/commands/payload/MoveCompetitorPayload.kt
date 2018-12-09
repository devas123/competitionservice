package compman.compsrv.model.es.commands.payload

import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO

data class MoveCompetitorPayload(val competitorId: String, val sourceFightId: String, val targetFightId: String, val index: Int)