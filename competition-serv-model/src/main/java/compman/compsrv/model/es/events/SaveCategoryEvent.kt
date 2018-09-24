package compman.compsrv.model.es.events

import compman.compsrv.model.competition.FightDescription


class SaveCategoryEvent {
    var categoryId: String? = ""
    var fights: Array<FightDescription>? = emptyArray()
}