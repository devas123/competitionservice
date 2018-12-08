package compman.compsrv.model.es.commands.payload

import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO

data class ChangeCompetitorCategoryPayload(val newCategory: CategoryDTO, val fighter: Competitor)