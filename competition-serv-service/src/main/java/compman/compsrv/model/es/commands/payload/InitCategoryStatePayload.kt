package compman.compsrv.model.es.commands.payload

import compman.compsrv.model.competition.CategoryDescriptor

data class InitCategoryStatePayload(val category: CategoryDescriptor)