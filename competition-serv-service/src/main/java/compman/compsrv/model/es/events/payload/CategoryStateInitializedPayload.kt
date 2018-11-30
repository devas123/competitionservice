package compman.compsrv.model.es.events.payload

import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.FightDescription

data class CategoryStateInitializedPayload(val categoryState: CategoryState)